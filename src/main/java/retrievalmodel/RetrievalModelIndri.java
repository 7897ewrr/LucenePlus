package retrievalmodel;

/**
 * Created by Tony on 09/28/14.
 */
public class RetrievalModelIndri extends RetrievalModel {
  public double mu = 0.0;
  public double lambda = 0.0;

  /**
   * Construct a retrieval model.
   *
   * @param mu_value
   * @param lambda_value
   */
  protected RetrievalModelIndri(String mu_value, String lambda_value) {
    mu = Double.parseDouble(mu_value);
    lambda = Double.parseDouble(lambda_value);
  }

  public boolean setParameter(String parameterName, double value) {

    if (parameterName.equals("mu") && value >= 0.0 && value <= 1.0) {
      mu = value;
      return true;
    } else if (parameterName.equals("lambda") && value >= 0.0) {
      lambda = value;
      return true;
    }

    return false;
  }

  /**
   * Set a retrieval model parameter.
   *
   * @param parameterName
   * @param value
   * @return
   */
  public boolean setParameter(String parameterName, String value) {

    double value1 = Double.parseDouble(value);
    if (parameterName.equals("mu") && value1 >= 0.0 && value1 <= 1.0) {
      mu = value1;
      return true;
    } else if (parameterName.equals("lambda") && value1 >= 0.0) {
      lambda = value1;
      return true;
    }


    return false;
  }

  public double getParameter(String parameterName) {

    if (parameterName.equals("mu")) {
      return mu;
    } else if (parameterName.equals("lambda")) {
      return lambda;
    }


    return 0;
  }
}
