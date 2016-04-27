package qryop;

import luceneplus.QryResult;
import retrievalmodel.RetrievalModel;
import util.InvList;
import util.ScoreList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class Qryop {

  //  DaaTPtrs are used by query operators for Document-at-a-Time (DAAT)
  //  query evaluation
  public int count = 0;


  protected class DaaTPtr {
    protected ScoreList scoreList;    // A qry arg's score list (if any)
    protected InvList invList;        // A qry arg's inverted list (if any)
    protected int nextDoc;        // The next document to examine
  }


  ;

  //  Initially the query operator starts with no arguments and no
  //  DaaTPtrs.

  public ArrayList<Qryop> args = new ArrayList<Qryop>();
  protected List<DaaTPtr> daatPtrs = new ArrayList<DaaTPtr>();
  ArrayList<Double> weight = new ArrayList<Double>();


  public void delete() {
  }

  /**
   * Appends an argument to the list of query operator arguments.  This
   * simplifies the design of some query parsing architectures.
   *
   * @param {q} q The query argument (query operator) to append.
   * @return void
   * @throws IOException
   */
  public abstract void add(Qryop q) throws IOException;

  public void add(Double a) {
    weight.add(a);
  }

  /**
   * Use the specified retrieval model to evaluate the query arguments.
   * Define and return DaaT pointers that the query operator can use.
   *
   * @param r A retrieval model that controls how the operator behaves.
   * @return The daatPtrs.
   * @throws IOException
   */
  public abstract void allocDaaTPtrs(RetrievalModel r) throws IOException;

  /**
   * Evaluates the query operator, including any child operators and
   * returns the result.
   *
   * @param r A retrieval model that controls how the operator behaves.
   * @return The result of evaluating the query.
   * @throws IOException
   */
  public abstract QryResult evaluate(RetrievalModel r) throws IOException;

  /**
   * Free this operator's DaaT pointers.
   *
   * @return void
   */
  public void freeDaaTPtrs() {
    this.daatPtrs = new ArrayList<DaaTPtr>();
  }

  /**
   * Removes an argument from the list of query operator arguments.
   * This simplifies the design of some query parsing architectures.
   *
   * @param i The index of the query operator to remove.
   * @return void
   */
  public void remove(int i) {
    this.args.remove(i);
  }

  ;

  /*
   *  Return a string version of this query operator.
   *  @return The string version of this query operator.
   */
  public abstract String toString();

}
