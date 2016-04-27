package luceneplus;

import qryop.*;
import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import retrievalmodel.*;
import util.Configuration;

import java.io.*;
import java.util.*;

/**
 * The main class of the application
 */
public class QryEval {

  final static Logger logger = Logger.getLogger(QryEval.class);

  static String usage = "Usage:  java " + System.getProperty("sun.java.command") + " paramFile\n\n";

  //  The index file reader is accessible via a global variable. This
  //  isn't great programming style, but the alternative is for every
  //  query operator to store or pass this value, which creates its
  //  own headaches.

  public static IndexReader READER;

  //  Create and configure an English analyzer that will be used for
  //  query parsing.

  public static EnglishAnalyzerConfigurable analyzer =
      new EnglishAnalyzerConfigurable(Version.LUCENE_43);

  static {
    analyzer.setLowercase(true);
    analyzer.setStopwordRemoval(true);
    analyzer.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);
  }

  /**
   * @param args The only argument is the path to the parameter file.
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    logger.info("System Start");

    long startTime = System.currentTimeMillis();

    // must supply parameter file
    if (args.length < 1) {
      System.err.println(usage);
      System.exit(1);
    }

    // read in the parameter file; one parameter per line in format of key=value
    Configuration configuration = Configuration.getConfig(args[0]);

    // open the index
    READER = DirectoryReader.open(FSDirectory.open(new File(configuration.getIndexPath())));
    if (READER == null) {
      System.err.println(usage);
      System.exit(1);
    }

    DocLengthStore dls = new DocLengthStore(READER);

    //set retrieval model
    String retrievalAlgorithm = configuration.getRetrievalAlgorithm();
    RetrievalModel model = RetrievalModelFactory.getRetrievalModel(retrievalAlgorithm, configuration);

    File queryFile = new File(configuration.getQueryFilePath());
    BufferedReader queryReader = null;
    HashMap<Integer, Double> docScore;
    HashMap<String, double[]> vector;
    LinkedList<String> qidList = new LinkedList<String>();
    HashMap<String, LinkedList<String>> qidDoc;
    ArrayList<Integer> disable = new ArrayList<Integer>();
    if (!configuration.getLetor_featureDisable().equals("null")) {
      String[] disabledFeatures = configuration.getLetor_featureDisable().split(",");
      for (int i = 0; i < disabledFeatures.length; i++) {
        disable.add(Integer.parseInt(disabledFeatures[i]));
      }
    }

    logger.info("read params complete");

    //Lear to Rank
    if (retrievalAlgorithm.equals("letor")) {
      logger.info("start Learn To Rank");
      LearnToRank mlModel = new LearnToRank(configuration, disable);
      mlModel.generateVectors();
      mlModel.train();

      qidDoc = new HashMap<String, LinkedList<String>>();
      vector = new HashMap<String, double[]>();

      queryReader = new BufferedReader(new FileReader(queryFile));
      BufferedWriter originDocVectorWriter = new BufferedWriter(
          new FileWriter(new File(configuration.getLetor_testingFeatureVectorsFile())));
      //read each query
      String tempString;
      while ((tempString = queryReader.readLine()) != null) {
        String[] originQuery = tempString.split(":");
        int qid = Integer.parseInt(originQuery[0].trim());
        qidList.add(qid + "");
        Qryop qTree;
        String query = new String(originQuery[1]);
        docScore = new HashMap<Integer, Double>();
        model = RetrievalModelFactory.getRetrievalModel("BM25", configuration);
        qTree = parseQuery(query, model, "body");
        getInitRanking(qid, qTree.evaluate(model), vector, qidDoc);
        mlModel
            .generateOriginDocFeatureVector(originDocVectorWriter, qidDoc, vector, query, docScore,
                originQuery);
      }
      originDocVectorWriter.close();
      queryReader.close();

      //predict
      mlModel.predict();

      //get new score from re-rank
      BufferedReader svmScoreReader =
          new BufferedReader(new FileReader(new File(configuration.getLetor_testingDocumentScores())));
      BufferedWriter finalReRankWriter =
          new BufferedWriter(new FileWriter(new File(configuration.getTrecEvalOutputPath())));
      for (int i = 0; i < qidList.size(); i++) {
        String qid = qidList.get(i);
        LinkedList<String> doc = qidDoc.get(qid);
        HashMap<String, Double> scores = new HashMap<String, Double>();
        for (int j = 0; j < 100 && j < doc.size(); j++) {
          tempString = svmScoreReader.readLine();
          scores.put(doc.get(j), Double.parseDouble(tempString));
        }
        printFinalResults(Integer.parseInt(qid), scores, finalReRankWriter, 100);
      }
      finalReRankWriter.close();
    } else {
      logger.info("The model is not LearnToRank");
      queryReader = new BufferedReader(new FileReader(queryFile));
      String tempString;
      BufferedWriter outputWriter;
      BufferedReader rankReader = null;
      BufferedWriter queryWriter = null;
      outputWriter = new BufferedWriter(new FileWriter(new File(configuration.getTrecEvalOutputPath())));
      if (!configuration.getFb().equals("null") && configuration.getFb().equals("true")) {
        queryWriter =
            new BufferedWriter(new FileWriter(new File(configuration.getFbExpansionQueryFile())));
        if (!configuration.getFbInitialRankingFile().equals("null")) {
          rankReader =
              new BufferedReader(new FileReader(new File(configuration.getFbInitialRankingFile())));
        }
      }
      while ((tempString = queryReader.readLine()) != null) {
        String[] originQuery = tempString.split(":");
        int qid = Integer.parseInt(originQuery[0].trim());
        Qryop qTree;
        String query = new String(originQuery[1]);
        docScore = new HashMap<Integer, Double>();
        qTree = parseQuery(query, model, null);
        // if using query expansion from feedback
        if (!configuration.getFb().equals("null") && configuration.getFb().equals("true")) {
          logger.info("Using query expansion. Processing query #" + qid);
          if (rankReader == null) {
            printFeedbackResults(qid, qTree.evaluate(model), outputWriter, docScore,
                Integer.parseInt(configuration.getFbDocs()));
          } else {
            getRank(qid, rankReader, docScore, Integer.parseInt(configuration.getFbDocs()));
          }

          String queryFromFb = new QueryExpansion(docScore, Integer.parseInt(configuration.getFbMu()),
              Integer.parseInt(configuration.getFbTerms()), dls).newQuery();
          logger.debug("queryFromFb:" + queryFromFb);
          logger.debug("search again using new query");
          queryWriter.write(qid + ":" + queryFromFb);
          String newQuery =
              "#wand (" + configuration.getFbOrigWeight() + " " + "#and(" + query + ") " + (1 - Double
                  .parseDouble(configuration.getFbOrigWeight())) + " " + queryFromFb;
          //logger.info("");
          Qryop newQTree = parseQuery(newQuery, model, null);
          printResults(qid, newQTree.evaluate(model), outputWriter, 100);
        } else {
          logger.info("Processing query #" + qid);
          printResults(qid, qTree.evaluate(model), outputWriter, 100);
        }
      }
      outputWriter.close();
      if (queryWriter != null) {
        queryWriter.close();
      }
      queryReader.close();
    }

    printMemoryUsage(true);
    printTimeUsage(startTime);
  }



  /**
   * parseQuery converts a query string into a query tree.
   *
   * @param qString A string containing a query.
   * @param model   A retrievalmodel.retrievalmodel
   * @param field
   * @throws IOException
   */
  static Qryop parseQuery(String qString, RetrievalModel model, String field) throws IOException {
    /**
     *  The index is open. Start evaluating queries. The examples
     *  below show query trees for two simple queries.  These are
     *  meant to illustrate how query nodes are created and connected.
     *  However your software will not create queries like this.  Your
     *  software will use a query parser.  See parseQuery.
     *
     *  The general pattern is to tokenize the  query term (so that it
     *  gets converted to lowercase, stopped, stemmed, etc), create a
     *  Term node to fetch the inverted list, create a Score node to
     *  convert an inverted list to a score list, evaluate the query,
     *  and print results.
     *
     *  Modify the software so that you read a query from a file,
     *  parse it, and form the query tree automatically.
     */
    Qryop currentOp = null;
    Stack<Qryop> stack = new Stack<Qryop>();

    qString = qString.trim();

    if (qString.charAt(0) != '#' || qString.charAt(qString.length() - 1) != ')') {
      if (model instanceof RetrievalModelBM25)
        qString = "#sum(" + qString + ")";
      else if (model instanceof RetrievalModelIndri)
        qString = "#and(" + qString + ")";
      else
        qString = "#or(" + qString + ")";
    } else if (model instanceof RetrievalModelBM25 && !qString.substring(0, 4)
        .equalsIgnoreCase("#sum")) {
      qString = "#sum(" + qString + ")";
    } else if (model instanceof RetrievalModelIndri && !qString.substring(0, 4)
        .equalsIgnoreCase("#and") && !qString.substring(0, 5).equalsIgnoreCase("#wand") && !qString
        .substring(0, 5).equalsIgnoreCase("#wsum")) {
      qString = "#and(" + qString + ")";
    } else if (qString.substring(0, 5).equalsIgnoreCase("#near") || qString.substring(0, 7)
        .equalsIgnoreCase("#window")) {
      qString = "#or(" + qString + ")";
    } else if (model instanceof RetrievalModelRankedBoolean
        && model instanceof RetrievalModelRankedBoolean) {
      if (!qString.substring(0, 4).equalsIgnoreCase("#and") || !qString.substring(0, 3)
          .equalsIgnoreCase("#or"))
        qString = "#or(" + qString + ")";
    }

    // Tokenize the query.

    StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
    String token;

    // Each pass of the loop processes one token. To improve
    // efficiency and clarity, the query operator on the top of the
    // stack is also stored in currentOp.
    while (tokens.hasMoreTokens()) {

      token = tokens.nextToken();
      if (token.matches("[ ,(\t\n\r]")) {
        // Ignore most delimiters.
      } else if (token.equalsIgnoreCase("#and")) {
        currentOp = new QryopSlAnd();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#syn")) {
        currentOp = new QryopIlSyn();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#or")) {
        currentOp = new QryopSlOr();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#sum")) {
        currentOp = new QryopSlSum();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#wand")) {
        currentOp = new QryopSlWand();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#wsum")) {
        currentOp = new QryopSlWsum();
        stack.push(currentOp);
      } else {
        String[] fen = token.split("/");
        if (fen[0].equalsIgnoreCase("#Near")) {
          int n = Integer.parseInt(fen[1]);
          currentOp = new QryopIlNear(n);
          stack.push(currentOp);
        } else if (fen[0].equalsIgnoreCase("#Window")) {
          int n = Integer.parseInt(fen[1]);
          currentOp = new QryopIlWindow(n);
          stack.push(currentOp);
        } else if (token.startsWith(")")) {

          stack.pop();

          if (stack.empty())
            break;

          Qryop arg = currentOp;
          currentOp = stack.peek();
          if (arg.args.size() > 0)
            currentOp.add(arg);
        } else {

          if (tokenizeQuery(token).length > 0) {
            if (currentOp instanceof QryopSlWand || currentOp instanceof QryopSlWsum) {
              if (currentOp.count % 2 == 0) {
                currentOp.add(Double.parseDouble(token));
                continue;
              }
            }
            String[] tokes = token.split("\\.");
            if (tokes.length == 2) {
              if (tokenizeQuery(tokes[0]).length > 0) {
                if (field == null) {
                  currentOp.add(new QryopIlTerm(tokenizeQuery(tokes[0])[0], tokes[1]));
                } else {
                  currentOp.add(new QryopIlTerm(tokenizeQuery(tokes[0])[0], field));
                }
              } else if (currentOp instanceof QryopSlWand || currentOp instanceof QryopSlWsum) {
                if (currentOp.count > 0)
                  currentOp.delete();
              }
            } else {
              if (field == null) {
                currentOp.add(new QryopIlTerm(tokenizeQuery(token)[0]));
              } else {
                currentOp.add(new QryopIlTerm(tokenizeQuery(token)[0], field));
              }
            }
          } else if (currentOp instanceof QryopSlWand || currentOp instanceof QryopSlWsum) {
            if (currentOp.count > 0)
              currentOp.delete();
          }
        }
      }
    }

    if (tokens.hasMoreTokens()) {
      System.err.println("Error:  Query syntax is incorrect.  " + qString);
      return null;
    }

    return currentOp;
  }

  static void getRank(int qid, BufferedReader rankReader, HashMap<Integer, Double> docScore,
      int fbDocs) {
    String tempString;
    int i = 0;
    try {
      while ((tempString = rankReader.readLine()) != null) {
        String[] id = tempString.split(" ");
        if (qid != Integer.parseInt(id[0])) {
          continue;
        }
        try {
          docScore.put(getInternalDocId(id[2]), Double.parseDouble(id[4]));
        } catch (Exception e) {
        }
        i++;
        if (i >= fbDocs)
          break;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  static void printFeedbackResults(int qid, QryResult result, BufferedWriter writer,
      HashMap<Integer, Double> docScore, int fbDocsNum) throws IOException {

    HashMap<String, Double> m = new HashMap<String, Double>();
    if (result.docScores.scores.size() < 1) {

    } else {
      for (int i = 0; i < result.docScores.scores.size(); i++) {
        m.put(getExternalDocId(result.docScores.getDocId(i)), result.docScores.getDocIdScore(i));
      }
      List<Map.Entry<String, Double>> infoIds =
          new ArrayList<Map.Entry<String, Double>>(m.entrySet());
      Collections.sort(infoIds, new Comparator<Map.Entry<String, Double>>() {
        public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
          if (o1.getValue() > o2.getValue()) {
            return -1;
          } else if (o1.getValue() < o2.getValue()) {
            return 1;
          } else
            return (o1.getKey()).toString().compareTo(o2.getKey().toString());
        }
      });

      for (int i = 0; i < infoIds.size() && i < fbDocsNum; i++) {
        String[] id = infoIds.get(i).toString().split("=");
        try {
          docScore.put(getInternalDocId(id[0]), Double.parseDouble(id[1]));
        } catch (Exception e) {
        }
        System.out.println(qid + "\t" + "Q0\t" + id[0] + "\t" + (i + 1) + "\t" + id[1]);

      }
    }
  }

  static void generateResultsForTraining(int qid, QryResult result, int featureId,
      HashMap<Integer, Double> docScore, HashMap<String, double[]> vector) throws IOException {

    HashMap<String, Double> m = new HashMap<String, Double>();
    if (result.docScores.scores.size() >= 1) {
      for (int i = 0; i < result.docScores.scores.size(); i++) {
        m.put(getExternalDocId(result.docScores.getDocId(i)), result.docScores.getDocIdScore(i));
      }
      List<Map.Entry<String, Double>> infoIds =
          new ArrayList<Map.Entry<String, Double>>(m.entrySet());
      Collections.sort(infoIds, new Comparator<Map.Entry<String, Double>>() {
        public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
          if (o1.getValue() > o2.getValue()) {
            return -1;
          } else if (o1.getValue() < o2.getValue()) {
            return 1;
          } else
            return (o1.getKey()).toString().compareTo(o2.getKey().toString());
        }
      });

      for (int i = 0; i < infoIds.size(); i++) {
        String[] id = infoIds.get(i).toString().split("=");
        if (vector.containsKey(qid + "" + id[0])) {
          double[] tmp = vector.get(qid + "" + id[0]);
          tmp[featureId] = Double.parseDouble(id[1]);
          vector.put(qid + "" + id[0], tmp);
        }
      }
    }
  }

  static void getInitRanking(int qid, QryResult result, HashMap<String, double[]> vector,
      HashMap<String, LinkedList<String>> qidDoc) throws IOException {

    HashMap<String, Double> m = new HashMap<String, Double>();
    logger.debug("# of result:" + result.docScores.scores.size());
    if (result.docScores.scores.size() >= 1) {
      for (int i = 0; i < result.docScores.scores.size(); i++) {
        m.put(getExternalDocId(result.docScores.getDocId(i)), result.docScores.getDocIdScore(i));
      }
      List<Map.Entry<String, Double>> infoIds =
          new ArrayList<Map.Entry<String, Double>>(m.entrySet());
      Collections.sort(infoIds, new Comparator<Map.Entry<String, Double>>() {

        public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
          if (o1.getValue() > o2.getValue()) {
            return -1;
          } else if (o1.getValue() < o2.getValue()) {
            return 1;
          } else
            return (o1.getKey()).toString().compareTo(o2.getKey().toString());
        }
      });
      LinkedList<String> docList = new LinkedList<String>();
      for (int i = 0; i < infoIds.size() && i < 100; i++) {
        String[] id = infoIds.get(i).toString().split("=");
        docList.add(id[0]);
        double[] feature = new double[18];
        feature[4] = Double.parseDouble(id[1]);
        vector.put(qid + id[0], feature);
      }
      qidDoc.put(String.valueOf(qid), docList);
      logger.debug("qidDoc:" + qid + "" + docList.toString());
    }
  }

  static void printResults(int qid, QryResult result, BufferedWriter writer, int resultDocNum)
      throws IOException {

    HashMap<String, Double> m = new HashMap<String, Double>();
    if (result.docScores.scores.size() < 1) {
      System.out.println(qid + "\tQ0\tdummy\t1\t0\trun-1");
      writer.write(qid + "\tQ0\tdummy\t1\t0\trun-1");
    } else {
      for (int i = 0; i < result.docScores.scores.size(); i++) {
        m.put(getExternalDocId(result.docScores.getDocId(i)), result.docScores.getDocIdScore(i));
      }
      List<Map.Entry<String, Double>> infoIds =
          new ArrayList<Map.Entry<String, Double>>(m.entrySet());

      Collections.sort(infoIds, new Comparator<Map.Entry<String, Double>>() {
        @Override public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
          if (o1.getValue() > o2.getValue()) {
            return -1;
          } else if (o1.getValue() < o2.getValue()) {
            return 1;
          } else
            return (o1.getKey()).toString().compareTo(o2.getKey().toString());
        }
      });

      for (int i = 0; i < infoIds.size() && i < resultDocNum; i++) {
        String[] id = infoIds.get(i).toString().split("=");
        System.out.println(qid + "\t" + "Q0\t" + id[0] + "\t" + (i + 1) + "\t" + id[1]);
        writer.write(qid + "\t" + "Q0\t" + id[0] + "\t" + (i + 1) + "\t" + id[1] + "\trun-1\n");
      }
    }
  }

  static void printFinalResults(int qid, HashMap<String, Double> scores, BufferedWriter writer,
      int resultDocNum) throws IOException {

    HashMap<String, Double> m = new HashMap<String, Double>();
    if (scores.size() < 1) {
      System.out.println(qid + "\tQ0\tdummy\t1\t0\trun-1");
      writer.write(qid + "\tQ0\tdummy\t1\t0\trun-1");
    } else {
      List<Map.Entry<String, Double>> infoIds =
          new ArrayList<Map.Entry<String, Double>>(scores.entrySet());
      Collections.sort(infoIds, new Comparator<Map.Entry<String, Double>>() {
        @Override public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
          if (o1.getValue() > o2.getValue()) {
            return -1;
          } else if (o1.getValue() < o2.getValue()) {
            return 1;
          } else
            return (o1.getKey()).toString().compareTo(o2.getKey().toString());
        }
      });

      for (int i = 0; i < infoIds.size() && i < resultDocNum; i++) {
        String[] id = infoIds.get(i).toString().split("=");
        System.out.println(qid + "\t" + "Q0\t" + id[0] + "\t" + (i + 1) + "\t" + id[1]);
        writer.write(qid + "\t" + "Q0\t" + id[0] + "\t" + (i + 1) + "\t" + id[1] + "\trun-1\n");
      }
    }
  }


  /**
   * Given a query string, returns the terms one at a time with stopwords
   * removed and the terms stemmed using the Krovetz stemmer.
   * <p/>
   * Use this method to process raw query terms.
   *
   * @param query String containing query
   * @return Array of query tokens
   * @throws IOException
   */
  static String[] tokenizeQuery(String query) throws IOException {

    TokenStreamComponents comp = analyzer.createComponents("dummy", new StringReader(query));
    TokenStream tokenStream = comp.getTokenStream();

    CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
    tokenStream.reset();

    List<String> tokens = new ArrayList<String>();
    while (tokenStream.incrementToken()) {
      String term = charTermAttribute.toString();
      tokens.add(term);
    }
    return tokens.toArray(new String[tokens.size()]);
  }

  /**
   * Get the external document id for a document specified by an
   * internal document id. If the internal id doesn't exists, returns null.
   *
   * @param iid The internal document id of the document.
   * @throws IOException
   */
  static String getExternalDocId(int iid) throws IOException {
    Document d = QryEval.READER.document(iid);
    String eid = d.get("externalId");
    return eid;
  }

  /**
   * Finds the internal document id for a document specified by its
   * external id, e.g. clueweb09-enwp00-88-09710.  If no such
   * document exists, it throws an exception.
   *
   * @param externalId The external document id of a document.s
   * @return An internal doc id suitable for finding document vectors etc.
   * @throws Exception
   */
  static int getInternalDocId(String externalId) throws Exception {
    Query q = new TermQuery(new Term("externalId", externalId));
    IndexSearcher searcher = new IndexSearcher(QryEval.READER);
    TopScoreDocCollector collector = TopScoreDocCollector.create(1, false);
    searcher.search(q, collector);
    ScoreDoc[] hits = collector.topDocs().scoreDocs;

    if (hits.length < 1) {
      throw new Exception("External id not found.");
    } else {
      return hits[0].doc;
    }
  }

  /**
   * Print a message indicating the amount of memory used.  The
   * caller can indicate whether garbage collection should be
   * performed, which slows the program but reduces memory usage.
   *
   * @param gc If true, run the garbage collector before reporting.
   * @return void
   */
  public static void printMemoryUsage(boolean gc) {
    Runtime runtime = Runtime.getRuntime();
    if (gc) {
      runtime.gc();
    }
    logger.info("Memory used:  " +
        ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
  }
  public static void printTimeUsage(long startTime) {
    long endTime = System.currentTimeMillis();
    logger.info("Time used: " + (endTime - startTime));
  }

  /**
   * Write an error message and exit.  This can be done in other
   * ways, but I wanted something that takes just one statement so
   * that it is easy to insert checks without cluttering the code.
   *
   * @param message The error message to write before exiting.
   * @return void
   */
  public static void fatalError(String message) {
    System.err.println(message);
    System.exit(1);
  }
}
