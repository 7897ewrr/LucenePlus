package luceneplus;

import qryop.Qryop;
import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Terms;
import retrievalmodel.RetrievalModel;
import retrievalmodel.RetrievalModelBM25;
import retrievalmodel.RetrievalModelFactory;
import retrievalmodel.RetrievalModelIndri;
import util.Configuration;
import util.InvList;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import static luceneplus.QryEval.*;

/**
 * Created by Tony on 11/23/14.
 * Implementation of learnToRank
 */
public class LearnToRank {
  final static Logger logger = Logger.getLogger(LearnToRank.class);
  Configuration configuration;
  ArrayList<Integer> disable;

  protected LearnToRank(Configuration configuration, ArrayList<Integer> disable) {
    this.configuration = configuration;
    this.disable = disable;
  }

  public void generateVectors() throws Exception {

    LinkedList<String> qidList = new LinkedList<>();
    HashMap<String, LinkedList<String>> qidDoc = new HashMap<>();
    HashMap<String, double[]> vector = new HashMap<>();
    BufferedReader trainingJudgeReader =
        new BufferedReader(new FileReader(new File(configuration.getLetor_trainingQrelsFile())));
    String tempString;
    String currentQid = "0";
    LinkedList<String> docInQid = new LinkedList<String>();
    //read all doc in judge to map & set 1,2 of each doc
    while ((tempString = trainingJudgeReader.readLine()) != null) {
      String[] featureString = tempString.split(" ");
      double[] featureSpace = new double[19];
      featureSpace[18] = Double.parseDouble(featureString[3]);

      Document d = QryEval.READER.document(getInternalDocId(featureString[2]));
      int spamScore = Integer.parseInt(d.get("score"));
      String rawUrl = d.get("rawUrl");

      featureSpace[0] = (double) spamScore;
      featureSpace[1] = (double) rawUrl.split("/").length - 3;

      if (rawUrl.charAt(rawUrl.length() - 1) == '/')
        featureSpace[1] += 1;

      if (rawUrl.contains("wikipedia.org"))
        featureSpace[2] = 1.0;
      else
        featureSpace[2] = 0.0;

      if (rawUrl.contains(".org") || rawUrl.contains(".edu") || rawUrl.contains(".gov"))
        featureSpace[17] = 1.0;
      else
        featureSpace[17] = 0.0;

      if (!featureString[0].equals(currentQid) && !currentQid.equals("0")) {
        qidList.add(currentQid);
        qidDoc.put(currentQid, docInQid);
        docInQid = new LinkedList<String>();
        currentQid = featureString[0];

      } else if (!featureString[0].equals(currentQid))
        currentQid = featureString[0];
      vector.put(featureString[0] + featureString[2], featureSpace);
      docInQid.add(featureString[2]);
    }
    qidList.add(currentQid);
    qidDoc.put(currentQid, docInQid);
    trainingJudgeReader.close();

    BufferedReader pageRankReader =
        new BufferedReader(new FileReader(new File(configuration.getLetor_pageRankFile())));
    while ((tempString = pageRankReader.readLine()) != null) {
      String[] tmp = tempString.split("\t");

      for (int i = 0; i < qidList.size(); i++) {
        String qid = qidList.get(i);
        if (vector.containsKey(qid + tmp[0])) {
          double[] featureSpace = vector.get(qid + tmp[0]);
          featureSpace[3] = Double.parseDouble(tmp[1]);
          vector.put(qid + tmp[0], featureSpace);
        }
      }
    }
    pageRankReader.close();


    HashMap<Integer, Double> docScore;
    RetrievalModel model;

    BufferedReader trainingQueryReader =
        new BufferedReader(new FileReader(new File(configuration.getLetor_trainingQueryFile())));
    BufferedWriter featureVectorWriter = new BufferedWriter(
        new FileWriter(new File(configuration.getLetor_trainingFeatureVectorsFile())));
    while ((tempString = trainingQueryReader.readLine()) != null) {

      double[] featuresMax = new double[18];

      double[] featuresMin = new double[18];

      String[] originQuery = tempString.split(":");
      int qid = Integer.parseInt(originQuery[0].trim());
      logger.info("start generating vectors for training query #:" + qid);
      Qryop qTree;
      String query = new String(originQuery[1]);
      docScore = new HashMap<Integer, Double>();

      model = RetrievalModelFactory.getRetrievalModel("BM25", configuration);
      qTree = parseQuery(query, model, "body");
      generateResultsForTraining(qid, qTree.evaluate(model), 4, docScore, vector);

      qTree = parseQuery(query, model, "title");
      generateResultsForTraining(qid, qTree.evaluate(model), 7, docScore, vector);

      qTree = parseQuery(query, model, "url");
      generateResultsForTraining(qid, qTree.evaluate(model), 10, docScore, vector);

      qTree = parseQuery(query, model, "inlink");
      generateResultsForTraining(qid, qTree.evaluate(model), 13, docScore, vector);

      model = RetrievalModelFactory.getRetrievalModel("indri", configuration);
      qTree = parseQuery(query, model, "body");
      generateResultsForTraining(qid, qTree.evaluate(model), 5, docScore, vector);

      qTree = parseQuery(query, model, "title");
      generateResultsForTraining(qid, qTree.evaluate(model), 8, docScore, vector);

      qTree = parseQuery(query, model, "url");
      generateResultsForTraining(qid, qTree.evaluate(model), 11, docScore, vector);

      qTree = parseQuery(query, model, "inlink");
      generateResultsForTraining(qid, qTree.evaluate(model), 14, docScore, vector);

      //set Term overlap

      LinkedList<String> docs = qidDoc.get(qid + "");
      getFeature(qid, docs, vector, originQuery);
      normalize(qid, featuresMax, featuresMin, vector, docs);
      writeVectorToFile(qid, docs, featuresMax, featuresMin, vector, disable, featureVectorWriter,
          true);

    }

    trainingQueryReader.close();
    featureVectorWriter.close();
    logger.info("generate vectors of training queries done");

  }


  public void train() throws InterruptedException, IOException {
    logger.info("start training");
    Process cmdProc = Runtime.getRuntime().exec(
        new String[] {configuration.getLetor_svmRankLearnPath(), "-c", configuration.getLetor_svmRankParamC(),
            configuration.getLetor_trainingFeatureVectorsFile(), configuration.getLetor_svmRankModelFile()});

    int retValue = cmdProc.waitFor();
    if (retValue == 0) {
      logger.info("training done");
    }
  }

  public void predict() throws IOException, InterruptedException {
    logger.info("start predicting");
    Process cmdProc = Runtime.getRuntime().exec(
        new String[] {configuration.getLetor_svmRankClassifyPath(),
            configuration.getLetor_testingFeatureVectorsFile(), configuration.getLetor_svmRankModelFile(),
            configuration.getLetor_testingDocumentScores()});

    int retValue = cmdProc.waitFor();
    if (retValue == 0) {
      logger.info("predicting done");
    }
  }

  public void generateOriginDocFeatureVector(BufferedWriter originDocVectorWriter,
      HashMap<String, LinkedList<String>> qidDoc, HashMap<String, double[]> vector, String query,
      HashMap<Integer, Double> docScore, String[] originQuery) throws Exception {
    int qid = Integer.parseInt(originQuery[0].trim());
    logger.info("start generating vectors for original query #:" + qid);
    LinkedList<String> docInQid = qidDoc.get(qid + "");
    for (int i = 0; i < docInQid.size(); i++) {
      String externalDocId = docInQid.get(i);
      double[] feature = vector.get(qid + externalDocId);

      Document d = QryEval.READER.document(getInternalDocId(externalDocId));
      int spamScore = Integer.parseInt(d.get("score"));
      String rawUrl = d.get("rawUrl");

      feature[0] = (double) spamScore;
      feature[1] = (double) rawUrl.split("/").length - 3;

      if (rawUrl.charAt(rawUrl.length() - 1) == '/')
        feature[1] += 1;

      if (rawUrl.contains("wikipedia.org"))
        feature[2] = 1.0;
      else
        feature[2] = 0.0;

      if (rawUrl.contains(".org") || rawUrl.contains(".edu") || rawUrl.contains(".gov"))
        feature[17] = 1.0;
      else
        feature[17] = 0.0;

      vector.put(qid + externalDocId, feature);

    }

    double[] featuresMax = new double[18];

    double[] featuresMin = new double[18];

    RetrievalModel model;
    Qryop qTree;

    model = RetrievalModelFactory.getRetrievalModel("BM25", configuration);
    qTree = parseQuery(query, model, "title");
    generateResultsForTraining(qid, qTree.evaluate(model), 7, docScore, vector);

    qTree = parseQuery(query, model, "url");
    generateResultsForTraining(qid, qTree.evaluate(model), 10, docScore, vector);

    qTree = parseQuery(query, model, "inlink");
    generateResultsForTraining(qid, qTree.evaluate(model), 13, docScore, vector);

    model = RetrievalModelFactory.getRetrievalModel("indri", configuration);
    qTree = parseQuery(query, model, "body");
    generateResultsForTraining(qid, qTree.evaluate(model), 5, docScore, vector);

    qTree = parseQuery(query, model, "title");
    generateResultsForTraining(qid, qTree.evaluate(model), 8, docScore, vector);

    qTree = parseQuery(query, model, "url");
    generateResultsForTraining(qid, qTree.evaluate(model), 11, docScore, vector);

    qTree = parseQuery(query, model, "inlink");
    generateResultsForTraining(qid, qTree.evaluate(model), 14, docScore, vector);


    LinkedList<String> docs = qidDoc.get(qid + "");
    getFeature(qid, docs, vector, originQuery);

    normalize(qid, featuresMax, featuresMin, vector, docs);

    writeVectorToFile(qid, docs, featuresMax, featuresMin, vector, disable, originDocVectorWriter,
        false);
  }

  public static void writeVectorToFile(int qid, LinkedList<String> docs, double[] featuresMax,
      double[] featuresMin, HashMap<String, double[]> vector, ArrayList<Integer> disable,
      BufferedWriter VectorWriter, boolean isTrainDoc) throws IOException {
    for (int i = 0; i < docs.size(); i++) {

      double[] features = vector.get(qid + docs.get(i));
      for (int j = 0; j < 18; j++) {
        if (j == 3 && features[j] == 0.0)
          continue;
        if (features[j] == -1000.0)
          features[j] = 0.0;
        else if (featuresMin[j] == featuresMax[j])
          features[j] = 0.0;
        else
          features[j] = (features[j] - featuresMin[j]) / (featuresMax[j] - featuresMin[j]);

        if (disable.contains(j + 1))
          features[j] = 0.0;


      }
      int label = 0;
      if (isTrainDoc) {
        label = (int) features[18];
      }
      VectorWriter.write(
          label + " qid:" + qid + " 1:" + features[0] + " 2:" + features[1] + " 3:" + features[2]
              + " 4:" + features[3] + " 5:" + features[4] + " 6:" + features[5] + " 7:"
              + features[6] + " 8:" + features[7] + " 9:" + features[8] + " 10:" + features[9]
              + " 11:" + features[10] + " 12:" + features[11] + " 13:" + features[12] + " 14:"
              + features[13] + " 15:" + features[14] + " 16:" + features[15] + " 17:" + features[16]
              + " 18:" + features[17] + " # " + docs.get(i) + "\n");

    }
  }

  public static void getFeature(int qid, LinkedList<String> docs, HashMap<String, double[]> vector,
      String[] originQuery) throws Exception {
    ArrayList<Integer> innerDocIds = new ArrayList<Integer>();
    for (int i = 0; i < docs.size(); i++) {
      innerDocIds.add(getInternalDocId(docs.get(i)));
    }

    String[] qTerm = originQuery[1].split(" ");
    InvList ivt;
    int termNum = 0;
    for (int j = 0; j < qTerm.length; j++) {
      if (tokenizeQuery(qTerm[j]).length > 0) {
        termNum++;
      }
    }
    for (int j = 0; j < qTerm.length; j++) {
      if (tokenizeQuery(qTerm[j]).length > 0) {
        ivt = new InvList(tokenizeQuery(qTerm[j])[0], "body");

        for (int k = 0; k < ivt.postings.size(); k++) {
          if (innerDocIds.contains(ivt.postings.get(k).docId)) {
            String externalDocId = getExternalDocId(ivt.postings.get(k).docId);
            double[] feature = vector.get(qid + externalDocId);
            feature[6] += 1.0 / termNum;
            vector.put(qid + externalDocId, feature);
          }

        }
        ivt = new InvList(tokenizeQuery(qTerm[j])[0], "title");
        for (int k = 0; k < ivt.postings.size(); k++) {
          if (innerDocIds.contains(ivt.postings.get(k).docId)) {
            String externalDocId = getExternalDocId(ivt.postings.get(k).docId);
            double[] feature = vector.get(qid + externalDocId);
            feature[9] += 1.0 / termNum;
            vector.put(qid + externalDocId, feature);


          }

        }
        ivt = new InvList(tokenizeQuery(qTerm[j])[0], "url");
        for (int k = 0; k < ivt.postings.size(); k++) {
          if (innerDocIds.contains(ivt.postings.get(k).docId)) {
            String externalDocId = getExternalDocId(ivt.postings.get(k).docId);
            double[] feature = vector.get(qid + externalDocId);
            feature[12] += 1.0 / termNum;
            vector.put(qid + externalDocId, feature);

          }

        }
        ivt = new InvList(tokenizeQuery(qTerm[j])[0], "inlink");
        for (int k = 0; k < ivt.postings.size(); k++) {
          if (innerDocIds.contains(ivt.postings.get(k).docId)) {
            String externalDocId = getExternalDocId(ivt.postings.get(k).docId);
            double[] feature = vector.get(qid + externalDocId);
            feature[15] += 1.0 / termNum;
            vector.put(qid + externalDocId, feature);

          }

        }
      }

    }

    for (int i = 0; i < docs.size(); i++) {

      double[] features = vector.get(qid + docs.get(i));
      Terms terms = QryEval.READER.getTermVector(getInternalDocId(docs.get(i)), "body");
      if (terms == null) {
        features[4] = -1000.0;
        features[5] = -1000.0;
        features[6] = -1000.0;
      }
      terms = QryEval.READER.getTermVector(getInternalDocId(docs.get(i)), "title");
      if (terms == null) {
        features[7] = -1000.0;
        features[8] = -1000.0;
        features[9] = -1000.0;
      }
      terms = QryEval.READER.getTermVector(getInternalDocId(docs.get(i)), "url");
      if (terms == null) {
        features[10] = -1000.0;
        features[11] = -1000.0;
        features[12] = -1000.0;
      }
      terms = QryEval.READER.getTermVector(getInternalDocId(docs.get(i)), "inlink");
      if (terms == null) {
        features[13] = -1000.0;
        features[14] = -1000.0;
        features[15] = -1000.0;
      }

    }
  }


  public static void normalize(int qid, double[] featuresMax, double[] featuresMin,
      HashMap<String, double[]> vector, LinkedList<String> docs) {
    for (int i = 0; i < docs.size(); i++) {
      double[] features = vector.get(qid + docs.get(i));
      for (int j = 0; j < 18; j++) {
        if (j == 3 && features[j] == 0.0)
          continue;
        if (features[j] == -1000.0)
          continue;
        if (features[j] < featuresMin[j])
          featuresMin[j] = features[j];
        if (features[j] > featuresMax[j])
          featuresMax[j] = features[j];
      }

    }
  }

}
