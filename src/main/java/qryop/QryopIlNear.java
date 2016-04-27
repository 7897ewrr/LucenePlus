package qryop;

import luceneplus.QryResult;
import retrievalmodel.RetrievalModel;
import util.InvList;

import java.io.IOException;
import java.util.LinkedList;

/**
 * Created by Tony on 09/13/14.
 */
public class QryopIlNear extends QryopIl {

  int nearPosition;

  public QryopIlNear(int nearPosition) {
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
   * @throws IOException
   */
  public QryResult evaluateBoolean(RetrievalModel r) throws IOException {

    allocDaaTPtrs(r);
    QryResult result = new QryResult();

    int tmpTf = 0;
    boolean signal = false;
    InvList invL1 = this.daatPtrs.get(0).invList;
    InvList invL2 = new InvList();
    LinkedList<Integer> posi = new LinkedList<Integer>();
    for (int j = 1; j < this.daatPtrs.size(); j++) {
      DaaTPtr ptrj = this.daatPtrs.get(j);
      int docindex1 = 0, docindex2 = 0;
      while (docindex1 < invL1.df && docindex2 < ptrj.invList.df) {
        if (invL1.getDocId(docindex1) < ptrj.invList.getDocId(docindex2)) {
          docindex1++;
        } else if (invL1.getDocId(docindex1) > ptrj.invList.getDocId(docindex2)) {
          docindex2++;
        } else {
          int posi1 = 0, posi2 = 0;
          while (posi1 < invL1.postings.get(docindex1).positions.size()
              && posi2 < ptrj.invList.postings.get(docindex2).positions.size()) {
            if (ptrj.invList.postings.get(docindex2).positions.get(posi2) - invL1.postings
                .get(docindex1).positions.get(posi1) <= nearPosition &&
                ptrj.invList.postings.get(docindex2).positions.get(posi2) - invL1.postings
                    .get(docindex1).positions.get(posi1) > 0) {
              posi.add(ptrj.invList.postings.get(docindex2).positions.get(posi2));
              posi1++;
              posi2++;
              signal = true;
            } else if (invL1.postings.get(docindex1).positions.get(posi1) > ptrj.invList.postings
                .get(docindex2).positions.get(posi2))
              posi2++;
            else
              posi1++;
          }
          if (signal) {
            invL2.appendPosting(ptrj.invList.getDocId(docindex2), posi);
            signal = false;
            tmpTf++;
          }
          posi.clear();
          docindex1++;
          docindex2++;

        }
      }
      invL2.df = tmpTf;
      invL1 = invL2;
      invL2 = new InvList();
      tmpTf = 0;
    }
    result.invertedList = invL1;
    result.invertedList.field = this.daatPtrs.get(0).invList.field;
    freeDaaTPtrs();

    return result;
  }


  /*
   *  Return a string version of this query operator.
   *  @return The string version of this query operator.
   */
  public String toString() {

    String result = new String();

    for (int i = 0; i < this.args.size(); i++)
      result += this.args.get(i).toString() + " ";

    return ("#near/" + nearPosition + "( " + result + ")");
  }
}
