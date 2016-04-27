package retrievalmodel;

/**
 * Created by Tony on 09/13/14.
 */
public class RetrievalModelRankedBoolean extends RetrievalModel {
  protected RetrievalModelRankedBoolean() {
  }

  /**
   * Set a retrieval model parameter.
   *
   * @param parameterName
   * @param value
   * @return Always false because this retrieval model has no parameters.
   */
  public boolean setParameter(String parameterName, double value) {
    System.err.println("Error: Unknown parameter name for retrieval model " +
        "UnrankedBoolean: " +
        parameterName);
    return false;
  }

  /**
   * Set a retrieval model parameter.
   *
   * @param parameterName
   * @param value
   * @return Always false because this retrieval model has no parameters.
   */
  public boolean setParameter(String parameterName, String value) {
    System.err.println("Error: Unknown parameter name for retrieval model " +
        "RankedBoolean: " +
        parameterName);
    return false;
  }

  public double getParameter(String parameterName) {
    System.err.println("Error: Unknown parameter name for retrieval model " +
        "RankedBoolean: " +
        parameterName);
    return 0;
  }
}
