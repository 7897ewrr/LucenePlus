package qryop;

import luceneplus.DocLengthStore;
import luceneplus.QryEval;
import luceneplus.QryResult;
import retrievalmodel.RetrievalModel;
import retrievalmodel.RetrievalModelBM25;
import retrievalmodel.RetrievalModelIndri;
import retrievalmodel.RetrievalModelUnrankedBoolean;
import util.InvList;

import java.io.IOException;
import java.util.Iterator;

/**
 * Created by Tony on 09/25/14.
 */
public class QryopSlScore extends QryopSl {
  public DocLengthStore dls;
  public double mu;
  public double lambda;
  public int ctf;
  public String currentField;

  /**
   * Construct a new SCORE operator.  The SCORE operator accepts just
   * one argument.
   *
   * @param q The query operator argument.
   * @return @link{qryop.QryopSlScore}
   */
  public QryopSlScore(Qryop q) {
    this.args.add(q);
  }

  /**
   * Construct a new SCORE operator.  Allow a SCORE operator to be
   * created with no arguments.  This simplifies the design of some
   * query parsing architectures.
   *
   * @return @link{qryop.QryopSlScore}
   */
  public QryopSlScore() {
  }

  /**
   * Appends an argument to the list of query operator arguments.  This
   * simplifies the design of some query parsing architectures.
   *
   * @param a The query argument to append.
   */
  public void add(Qryop a) {
    this.args.add(a);
  }

  /**
   * Evaluate the query operator.
   *
   * @param r A retrieval model that controls how the operator behaves.
   * @return The result of evaluating the query.
   * @throws IOException
   */
  public QryResult evaluate(RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean)
      return evaluateBoolean(r);
    if (r instanceof RetrievalModelBM25)
      return evaluateBM25(r);
    if (r instanceof RetrievalModelIndri)
      return evaluateIndri(r);

    return evaluateRankedBoolean(r);
  }

  /**
   * Evaluate the query operator for boolean retrieval models.
   *
   * @param r A retrieval model that controls how the operator behaves.
   * @return The result of evaluating the query.
   * @throws IOException
   */
  public QryResult evaluateBoolean(RetrievalModel r) throws IOException {
    QryResult result = args.get(0).evaluate(r);
    for (int i = 0; i < result.invertedList.df; i++) {
      result.docScores.add(result.invertedList.postings.get(i).docId, (float) 1.0);
    }
    return result;
  }

  public QryResult evaluateRankedBoolean(RetrievalModel r) throws IOException {
    QryResult result = args.get(0).evaluate(r);
    for (int i = 0; i < result.invertedList.df; i++) {
      result.docScores
          .add(result.invertedList.postings.get(i).docId, result.invertedList.postings.get(i).tf);
    }
    if (result.invertedList.df > 0)
      result.invertedList = new InvList();

    return result;
  }

  public QryResult evaluateBM25(RetrievalModel r) throws IOException {

    QryResult result = args.get(0).evaluate(r);
    double b = r.getParameter("b");
    double k_1 = r.getParameter("k_1");
    double k_3 = r.getParameter("k_3");
    int n = QryEval.READER.numDocs();
    int df = result.invertedList.df;
    dls = new DocLengthStore(QryEval.READER);
    float avgDocLen =
        QryEval.READER.getSumTotalTermFreq(result.invertedList.field) / (float) QryEval.READER
            .getDocCount(result.invertedList.field);
    int qtf = 1;
    for (int i = 0; i < result.invertedList.df; i++) {
      long docLen = dls.getDocLength(result.invertedList.field, result.invertedList.getDocId(i));
      int tf = result.invertedList.getTf(i);
      double score =
          (Math.log((n - df + 0.5) / (df + 0.5))) * (tf / (tf + k_1 * ((1 - b) + b * (docLen
              / avgDocLen)))) * (((k_3 + 1) * qtf) / (k_3 + qtf));

      result.docScores.add(result.invertedList.postings.get(i).docId, score);
    }

    if (result.invertedList.df > 0)
      result.invertedList = new InvList();

    return result;
  }

  public QryResult evaluateIndri(RetrievalModel r) throws IOException {
    QryResult result = args.get(0).evaluate(r);

    mu = ((RetrievalModelIndri) r).mu;
    lambda = ((RetrievalModelIndri) r).lambda;
    ctf = result.invertedList.ctf;
    currentField = result.invertedList.field;
    dls = new DocLengthStore(QryEval.READER);
    long sumTf = QryEval.READER.getSumTotalTermFreq(result.invertedList.field);
    for (int i = 0; i < result.invertedList.df; i++) {
      long docLen = dls.getDocLength(result.invertedList.field, result.invertedList.getDocId(i));
      int docTf = result.invertedList.getTf(i);
      double score =
          lambda * ((docTf + mu * ((double) ctf / sumTf)) / (docLen + mu)) + (1 - lambda) * (
              (double) ctf / sumTf);
      result.docScores.add(result.invertedList.postings.get(i).docId, score);
    }
    if (result.invertedList.df > 0)
      result.invertedList = new InvList();
    return result;
  }

  /*
 *  Calculate the default score for a document that does not match
 *  the query argument.  This score is 0 for many retrieval models,
 *  but not all retrieval models.
 *  @param r A retrieval model that controls how the operator behaves.
 *  @param docId The internal id of the document that needs a default score.
 *  @return The default score.
 */
  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
    if (r instanceof RetrievalModelIndri) {
      long doclen = dls.getDocLength(currentField, (int) docid);
      double sumtf = QryEval.READER.getSumTotalTermFreq(currentField);
      double score =
          lambda * ((mu * ((double) ctf / sumtf)) / (doclen + mu)) + (1 - lambda) * ((double) ctf
              / sumtf);
      return score;
    }
    return 0.0;
  }

  /**
   * Return a string version of this query operator.
   *
   * @return The string version of this query operator.
   */
  public String toString() {
    String result = new String();
    for (Iterator<Qryop> i = this.args.iterator(); i.hasNext(); )
      result += (i.next().toString() + " ");
    return ("#SCORE( " + result + ")");
  }
}
