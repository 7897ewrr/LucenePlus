package retrievalmodel;

public abstract class RetrievalModel {

  /**
   * Set a retrieval model parameter.
   *
   * @param parameterName  The name of the parameter to set.
   * @param parametervalue The parameter's value.
   * @return true if the parameter is set successfully, false otherwise.
   */
  public abstract boolean setParameter(String parameterName, double parametervalue);

  /**
   * Set a retrieval model parameter.
   *
   * @param parameterName  The name of the parameter to set.
   * @param parametervalue The parameter's value.
   * @return true if the parameter is set successfully, false otherwise.
   */
  public abstract boolean setParameter(String parameterName, String parametervalue);

  public abstract double getParameter(String parameterName);
}
