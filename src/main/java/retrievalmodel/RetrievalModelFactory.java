package retrievalmodel;

import util.Configuration;

/**
 * Created by Tony on 9/25/14.
 */
public class RetrievalModelFactory {

  private static RetrievalModel unRankedBooleanModel;
  private static RetrievalModel bm25Model;
  private static RetrievalModel indriModel;
  private static RetrievalModel rankedBooleanModel;

  private RetrievalModelFactory() {
  }

  public static RetrievalModel getRetrievalModel(String type, Configuration configuration) {
    RetrievalModel model = null;
    if (type.equals("UnrankedBoolean")) {
      if (unRankedBooleanModel == null) {
        unRankedBooleanModel = new RetrievalModelUnrankedBoolean();
      }
      model = unRankedBooleanModel;
    } else if (type.equals("BM25")) {
      if (bm25Model == null) {
        bm25Model = new RetrievalModelBM25(configuration.getBM25_b(), configuration.getBM25_k_1(),
            configuration.getBM25_k_3());
      }
      model = bm25Model;
    } else if (type.equals("Indri")) {
      if (indriModel == null) {
        indriModel =
            new RetrievalModelIndri(configuration.getIndri_mu(), configuration.getIndri_lambda());
      }
      model = indriModel;
    } else if (type.equals("RankedBoolean")) {
      if (rankedBooleanModel == null) {
        rankedBooleanModel = new RetrievalModelRankedBoolean();
      }
      model = rankedBooleanModel;
    } else if (!type.equals("letor")) {
      System.err.println("Error: Input Wrong retrievalmodel.");
      System.exit(1);
    }
    return model;
  }
}
