package retrievalmodel;

/**
 * Created by Tony on 9/25/14.
 */
public class RetrievalModelBM25 extends RetrievalModel {
  public double b = 0.0;
  public double k_1 = 0.0;
  public double k_3 = 0.0;

  /**
   * Construct a retrieval model parameter.
   *
   * @param b_value
   * @param k_1_value
   * @param k_3_value
   */
  protected RetrievalModelBM25(String b_value, String k_1_value, String k_3_value) {
    b = Double.parseDouble(b_value);
    k_1 = Double.parseDouble(k_1_value);
    k_3 = Double.parseDouble(k_3_value);

  }

  public boolean setParameter(String parameterName, double value) {

    if (parameterName.equals("b") && value >= 0.0 && value <= 1.0) {
      b = value;
      return true;
    } else if (parameterName.equals("k_1") && value >= 0.0) {
      k_1 = value;
      return true;
    } else if (parameterName.equals("k_3") && value >= 0.0) {
      k_3 = value;
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
    if (parameterName.equals("b") && value1 >= 0.0 && value1 <= 1.0) {
      b = value1;
      return true;
    } else if (parameterName.equals("k_1") && value1 >= 0.0) {
      k_1 = value1;
      return true;
    } else if (parameterName.equals("k_3") && value1 >= 0.0) {
      k_3 = value1;
      return true;
    }

    return false;
  }

  public double getParameter(String parameterName) {

    if (parameterName.equals("b")) {
      return b;
    } else if (parameterName.equals("k_1")) {
      return k_1;
    } else if (parameterName.equals("k_3")) {
      return k_3;
    }

    return 0;
  }
}
