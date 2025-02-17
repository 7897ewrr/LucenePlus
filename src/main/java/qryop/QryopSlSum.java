package qryop;

import luceneplus.QryResult;
import retrievalmodel.RetrievalModel;
import retrievalmodel.RetrievalModelBM25;
import util.ScoreList;

import java.io.IOException;

/**
 * Created by Tony on 09/25/14.
 */
public class QryopSlSum extends QryopSl {
  /**
   * It is convenient for the constructor to accept a variable number
   * of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
   *
   * @param q A query argument (a query operator).
   */
  public QryopSlSum(Qryop... q) {
    for (int i = 0; i < q.length; i++)
      this.args.add(q[i]);
  }

  /**
   * Appends an argument to the list of query operator arguments.  This
   * simplifies the design of some query parsing architectures.
   *
   * @param {q} q The query argument (query operator) to append.
   * @return void
   * @throws java.io.IOException
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
   * @throws java.io.IOException
   */
  public QryResult evaluate(RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelBM25)
      return evaluateBM25(r);
    else
      System.err.print("not BM25");
    return null;

  }

  /**
   * Evaluates the query operator for boolean retrieval models,
   * including any child operators and returns the result.
   *
   * @param r A retrieval model that controls how the operator behaves.
   * @return The result of evaluating the query.
   * @throws IOException
   */
  public QryResult evaluateBM25(RetrievalModel r) throws IOException {

    allocDaaTPtrs(r);
    QryResult result = new QryResult();

    for (int i = 0; i < (this.daatPtrs.size() - 1); i++) {
      for (int j = i + 1; j < this.daatPtrs.size(); j++) {
        if (this.daatPtrs.get(i).scoreList.scores.size() < this.daatPtrs.get(j).scoreList.scores
            .size()) {
          ScoreList tmpScoreList = this.daatPtrs.get(i).scoreList;
          this.daatPtrs.get(i).scoreList = this.daatPtrs.get(j).scoreList;
          this.daatPtrs.get(j).scoreList = tmpScoreList;
        }
      }

    }

    QryResult result1 = new QryResult();
    QryResult result2 = new QryResult();

    for (int i = 0; i < this.daatPtrs.size(); i++) {
      int p1 = 0, p2 = 0;
      while (p1 < result2.docScores.scores.size() && p2 < this.daatPtrs.get(i).scoreList.scores
          .size()) {
        if (result2.docScores.getDocId(p1) < this.daatPtrs.get(i).scoreList.getDocId(p2)) {
          result1.docScores
              .add(result2.docScores.getDocId(p1), result2.docScores.getDocIdScore(p1));
          p1++;

        } else {
          if (result2.docScores.getDocId(p1) == this.daatPtrs.get(i).scoreList.getDocId(p2)) {
            result1.docScores.add(this.daatPtrs.get(i).scoreList.getDocId(p2),
                this.daatPtrs.get(i).scoreList.getDocIdScore(p2) + result2.docScores
                    .getDocIdScore(p1));
            p1++;
            p2++;
          } else {
            result1.docScores.add(this.daatPtrs.get(i).scoreList.getDocId(p2),
                this.daatPtrs.get(i).scoreList.getDocIdScore(p2));
            p2++;
          }
        }
      }
      while (p2 < this.daatPtrs.get(i).scoreList.scores.size()) {
        result1.docScores.add(this.daatPtrs.get(i).scoreList.getDocId(p2),
            this.daatPtrs.get(i).scoreList.getDocIdScore(p2));
        p2++;
      }
      while (p1 < result2.docScores.scores.size()) {
        result1.docScores.add(result2.docScores.getDocId(p1), result2.docScores.getDocIdScore(p1));
        p1++;
      }
      result2 = result1;
      result1 = new QryResult();
    }

    freeDaaTPtrs();
    result.docScores = result2.docScores;
    return result;
  }

  public double getDefaultScore(RetrievalModel r, long docId) throws IOException {
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
    return ("#sum" + "( " + result + ")");
  }
}
