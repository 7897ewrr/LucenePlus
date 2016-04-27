package qryop;

import luceneplus.QryResult;
import retrievalmodel.RetrievalModel;
import retrievalmodel.RetrievalModelIndri;
import retrievalmodel.RetrievalModelRankedBoolean;
import retrievalmodel.RetrievalModelUnrankedBoolean;
import util.ScoreList;

import java.io.IOException;

/**
 * Created by Tony on 09/11/14.
 */
public class QryopSlAnd extends QryopSl {
  public double mu;
  public double lambda;


  /**
   * It is convenient for the constructor to accept a variable number
   * of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
   *
   * @param q A query argument (a query operator).
   */

  public QryopSlAnd(Qryop... q) {
    for (int i = 0; i < q.length; i++)
      this.args.add(q[i]);
  }

  /**
   * Appends an argument to the list of query operator arguments.  This
   * simplifies the design of some query parsing architectures.
   *
   * @param {q} q The query argument (query operator) to append.
   * @return void
   * @throws IOException
   */
  public void add(Qryop a) {
    this.args.add(a);
  }

  /**
   * Evaluates the query operator, including any child operators and
   * returns the result.
   *
   * @param r A retrieval model that controls how the operator behaves.
   * @return The result of evaluating the query.
   * @throws IOException
   */
  public QryResult evaluate(RetrievalModel r) throws IOException {
    if (r instanceof RetrievalModelUnrankedBoolean) {
      return evaluateBooleanAndRankedBoolean(r);
    } else if (r instanceof RetrievalModelIndri) {
      return evaluateIndri(r);
    } else {
      return evaluateBooleanAndRankedBoolean(r);
    }
  }

  /**
   * Evaluates the query operator for boolean retrieval models,
   * including any child operators and returns the result.
   *
   * @param r A retrieval model that controls how the operator behaves.
   * @return The result of evaluating the query.
   * @throws IOException
   */

  public QryResult evaluateBooleanAndRankedBoolean(RetrievalModel r) throws IOException {

    allocDaaTPtrs(r);
    QryResult result = new QryResult();

    for (int i = 0; i < (this.daatPtrs.size() - 1); i++) {
      for (int j = i + 1; j < this.daatPtrs.size(); j++) {
        if (this.daatPtrs.get(i).scoreList.scores.size() > this.daatPtrs.get(j).scoreList.scores
            .size()) {
          ScoreList tmpScoreList = this.daatPtrs.get(i).scoreList;
          this.daatPtrs.get(i).scoreList = this.daatPtrs.get(j).scoreList;
          this.daatPtrs.get(j).scoreList = tmpScoreList;
        }
      }
    }

    DaaTPtr ptr0 = this.daatPtrs.get(0);
    EVALUATEDOCUMENTS:
    for (; ptr0.nextDoc < ptr0.scoreList.scores.size(); ptr0.nextDoc++) {

      int ptr0Docid = ptr0.scoreList.getDocId(ptr0.nextDoc);
      double docScore = 1.0;
      if (r instanceof RetrievalModelRankedBoolean) {
        docScore = ptr0.scoreList.getDocIdScore(ptr0.nextDoc);
      }
      for (int j = 1; j < this.daatPtrs.size(); j++) {

        DaaTPtr ptrj = this.daatPtrs.get(j);
        while (true) {
          if (ptrj.nextDoc >= ptrj.scoreList.scores.size())
            break EVALUATEDOCUMENTS;
          else if (ptrj.scoreList.getDocId(ptrj.nextDoc) > ptr0Docid)
            continue EVALUATEDOCUMENTS;
          else if (ptrj.scoreList.getDocId(ptrj.nextDoc) < ptr0Docid)
            ptrj.nextDoc++;
          else {
            if (r instanceof RetrievalModelRankedBoolean
                && ptrj.scoreList.getDocIdScore(ptrj.nextDoc) < docScore)
              docScore = ptrj.scoreList.getDocIdScore(ptrj.nextDoc);
            break;
          }
        }
      }

      result.docScores.add(ptr0Docid, docScore);
    }

    freeDaaTPtrs();
    return result;
  }


  public QryResult evaluateIndri(RetrievalModel r) throws IOException {
    mu = ((RetrievalModelIndri) r).mu;
    lambda = ((RetrievalModelIndri) r).lambda;

    allocDaaTPtrs(r);
    QryResult result = new QryResult();

    double score = 1.0;
    QryResult result1 = new QryResult();
    QryResult result2 = new QryResult();

    for (int i = 0; i < this.daatPtrs.size(); i++) {
      int p1 = 0, p2 = 0;
      while (p1 < result2.docScores.scores.size() && p2 < this.daatPtrs.get(i).scoreList.scores
          .size()) {
        if (result2.docScores.getDocId(p1) < this.daatPtrs.get(i).scoreList.getDocId(p2)) {
          result1.docScores.add(result2.docScores.getDocId(p1), score);
          p1++;

        } else {
          result1.docScores.add(this.daatPtrs.get(i).scoreList.getDocId(p2), score);
          p2++;
          if (result2.docScores.getDocId(p1) == this.daatPtrs.get(i).scoreList.getDocId(p2 - 1))
            p1++;
        }
      }
      while (p2 < this.daatPtrs.get(i).scoreList.scores.size()) {
        result1.docScores.add(this.daatPtrs.get(i).scoreList.getDocId(p2), score);
        p2++;
      }

      while (p1 < result2.docScores.scores.size()) {
        result1.docScores.add(result2.docScores.getDocId(p1), score);
        p1++;
      }
      result2 = result1;
      result1 = new QryResult();
    }

    result.docScores = result2.docScores;
    result2 = new QryResult();
    for (int i = 0; i < this.daatPtrs.size(); i++) {
      int p1 = 0, p2 = 0;
      while (p1 < result.docScores.scores.size()) {
        if (this.daatPtrs.get(i).scoreList.scores.size() < 1) {
          score = ((QryopSl) (this.args.get(i))).getDefaultScore(r, result.docScores.getDocId(p1));
          result2.docScores.add(result.docScores.getDocId(p1),
              result.docScores.getDocIdScore(p1) * Math.pow(score, (1.0 / this.daatPtrs.size())));
          p1++;
        } else if (result.docScores.getDocId(p1) < this.daatPtrs.get(i).scoreList.getDocId(p2)) {
          score = ((QryopSl) (this.args.get(i))).getDefaultScore(r, result.docScores.getDocId(p1));
          result2.docScores.add(result.docScores.getDocId(p1),
              result.docScores.getDocIdScore(p1) * Math.pow(score, (1.0 / this.daatPtrs.size())));
          p1++;
        } else if (result.docScores.getDocId(p1) == this.daatPtrs.get(i).scoreList.getDocId(p2)) {
          result2.docScores.add(result.docScores.getDocId(p1),
              result.docScores.getDocIdScore(p1) * Math
                  .pow(this.daatPtrs.get(i).scoreList.getDocIdScore(p2),
                      (1.0 / this.daatPtrs.size())));
          p1++;
          p2++;
          if (p2 == this.daatPtrs.get(i).scoreList.scores.size())
            p2--;
        } else {
          score = ((QryopSl) (this.args.get(i))).getDefaultScore(r, result.docScores.getDocId(p1));
          result2.docScores.add(result.docScores.getDocId(p1),
              result.docScores.getDocIdScore(p1) * Math.pow(score, (1.0 / this.daatPtrs.size())));
          p1++;
        }
      }
      result = result2;
      result2 = new QryResult();
    }
    freeDaaTPtrs();

    return result;
  }

  /*
   *  Calculate the default score for the specified document if it
   *  does not match the query operator.  This score is 0 for many
   *  retrieval models, but not all retrieval models.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @param docId The internal id of the document that needs a default score.
   *  @return The default score.
   */
  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean)
      return 0.0;
    if (r instanceof RetrievalModelIndri) {
      double score = 1.0;
      for (int i = 0; i < this.args.size(); i++) {
        score = score * Math
            .pow(((QryopSl) this.args.get(i)).getDefaultScore(r, docid), (1.0 / this.args.size()));
      }
      return score;

    }
    return 0.0;
  }

  /*
   *  Return a string version of this query operator.
   *  @return The string version of this query operator.
   */
  public String toString() {

    String result = new String();
    for (int i = 0; i < this.args.size(); i++)
      result += this.args.get(i).toString() + " ";
    return ("#AND( " + result + ")");
  }
}
