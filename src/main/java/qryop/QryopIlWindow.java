package qryop;

import luceneplus.QryResult;
import retrievalmodel.RetrievalModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Created by Tony on 10/15/14.
 */
public class QryopIlWindow extends QryopIl {
  /**
   * It is convenient for the constructor to accept a variable number
   * of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
   *
   * @param q A query argument (a query operator).
   */
  int nearPosition;

  public QryopIlWindow(int nearPosition) {
    this.nearPosition = nearPosition;
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
    return evaluateBoolean(r);
  }

  /**
   * Evaluates the query operator for boolean retrieval models,
   * including any child operators and returns the result.
   *
   * @param r A retrieval model that controls how the operator behaves.
   * @return The result of evaluating the query.
   * @throws java.io.IOException
   */
  public QryResult evaluateBoolean(RetrievalModel r) throws IOException {
    allocDaaTPtrs(r);
    QryResult result = new QryResult();
    int tmptf = 0;
    boolean testd;
    ArrayList<Integer> di = new ArrayList<Integer>();
    ArrayList<Integer> pi = new ArrayList<Integer>();
    LinkedList<Integer> posi = new LinkedList<Integer>();
    int dmin = 0;
    int dmini = 0;
    int pmin = 0;
    int pmini = 0;
    for (int i = 0; i < this.daatPtrs.size(); i++) {
      di.add(0);
      pi.add(0);
    }
    outer:
    while (true) {
      testd = true;
      if (di.get(0) >= this.daatPtrs.get(0).invList.postings.size()) {
        break outer;
      }
      dmin = this.daatPtrs.get(0).invList.getDocId(di.get(0));
      dmini = 0;
      for (int i = 1; i < this.daatPtrs.size(); i++) {
        if (di.get(i) >= this.daatPtrs.get(i).invList.postings.size()) {
          break outer;
        }
        if (this.daatPtrs.get(i).invList.getDocId(di.get(i)) != dmin) {
          testd = false;
          if (this.daatPtrs.get(i).invList.getDocId(di.get(i)) < dmin) {
            dmin = this.daatPtrs.get(i).invList.getDocId(di.get(i));
            dmini = i;
          }
        }


      }

      if (testd) {
        pi = new ArrayList<Integer>();
        for (int i = 0; i < this.daatPtrs.size(); i++) {
          pi.add(0);
        }

        int pmax = 0;
        boolean tag = false;
        posi.clear();
        nei:
        while (true) {
          if (pi.get(0) >= this.daatPtrs.get(0).invList.postings.get(di.get(0)).positions.size()) {
            break nei;
          }

          pmin = this.daatPtrs.get(0).invList.postings.get(di.get(0)).positions.get(pi.get(0));
          pmax = this.daatPtrs.get(0).invList.postings.get(di.get(0)).positions.get(pi.get(0));
          pmini = 0;
          for (int i = 1; i < this.daatPtrs.size(); i++) {
            if (pi.get(i) >= this.daatPtrs.get(i).invList.postings.get(di.get(i)).positions
                .size()) {
              break nei;
            }
            if (this.daatPtrs.get(i).invList.postings.get(di.get(i)).positions.get(pi.get(i))
                < pmin) {
              pmin = this.daatPtrs.get(i).invList.postings.get(di.get(i)).positions.get(pi.get(i));
              pmini = i;
            }
            int a = this.daatPtrs.get(i).invList.postings.get(di.get(i)).positions.get(pi.get(i));
            if (this.daatPtrs.get(i).invList.postings.get(di.get(i)).positions.get(pi.get(i))
                > pmax) {
              pmax = this.daatPtrs.get(i).invList.postings.get(di.get(i)).positions.get(pi.get(i));
            }


          }
          if ((pmax - pmin + 1) <= nearPosition) {
            posi.add(this.daatPtrs.get(this.daatPtrs.size() - 1).invList.postings
                .get(di.get(this.daatPtrs.size() - 1)).positions
                .get(pi.get(this.daatPtrs.size() - 1)));
            tag = true;
            for (int j = 0; j < this.daatPtrs.size(); j++) {

              pi.set(j, pi.get(j) + 1);

            }


          } else {
            pi.set(pmini, pi.get(pmini) + 1);
          }
        }
        if (tag) {
          result.invertedList.appendPosting(this.daatPtrs.get(this.daatPtrs.size() - 1).invList
              .getDocId(di.get(this.daatPtrs.size() - 1)), posi);
          tmptf++;
        }

        for (int j = 0; j < this.daatPtrs.size(); j++) {
          di.set(j, di.get(j) + 1);
        }
      } else {
        di.set(dmini, di.get(dmini) + 1);
      }
    }

    result.invertedList.df = tmptf;
    result.invertedList.field = this.daatPtrs.get(0).invList.field;

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

    return ("#window/" + nearPosition + "( " + result + ")");
  }
}
