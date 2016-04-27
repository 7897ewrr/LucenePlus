package util;

import lombok.Getter;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * generate configuration
 * Created by Tony on 12/02/14.
 */
public class Configuration {
  static final Logger logger = Logger.getLogger(Configuration.class);

  //basic parameters
  @Getter private String queryFilePath;
  @Getter private String indexPath;
  @Getter private String trecEvalOutputPath;
  @Getter private String retrievalAlgorithm;
  @Getter private String BM25_k_1;
  @Getter private String BM25_b;
  @Getter private String BM25_k_3;
  @Getter private String Indri_mu;
  @Getter private String Indri_lambda;

  // parameters for query expansion from feedback ###
  @Getter private String fb;
  @Getter private String fbDocs;
  @Getter private String fbTerms;
  @Getter private String fbMu;
  @Getter private String fbOrigWeight;
  @Getter private String fbInitialRankingFile;
  @Getter private String fbExpansionQueryFile;

  // parameters for LearnToRank ###
  @Getter private String letor_trainingQueryFile;
  @Getter private String letor_trainingQrelsFile;
  @Getter private String letor_featureDisable;
  @Getter private String letor_trainingFeatureVectorsFile;
  @Getter private String letor_pageRankFile;
  @Getter private String letor_testingFeatureVectorsFile;
  @Getter private String letor_testingDocumentScores;
  @Getter private String letor_svmRankLearnPath;
  @Getter private String letor_svmRankParamC;
  @Getter private String letor_svmRankModelFile;
  @Getter private String letor_svmRankClassifyPath;

  private Map<String, String> params;

  private static Configuration configuration ;

  public static Configuration getConfig(String paramsFile) throws Exception{
    if (configuration == null) {
      configuration = new Configuration(paramsFile);
    }
    return configuration;
  }

  private Configuration(String filePath) throws Exception {
    Map<String, String> params = new HashMap<String, String>();
    Scanner scan = new Scanner(new File(filePath));
    String line;
    do {
      line = scan.nextLine();
      if (line.startsWith("##") || line.length() == 0) {
        continue;
      }
      if (line.startsWith("#")) {
        logger.debug("ignore param:" + line);
        continue;
      }
      String[] pair = line.split("=");
      params.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());
    scan.close();
    // parameters required for this example to run
    if (!params.containsKey("indexPath")) {
      System.err.println("Error: Parameters were missing.");
      System.exit(1);
    }
    this.params = params;

    queryFilePath = addParam("queryFilePath");
    indexPath = addParam("indexPath");
    trecEvalOutputPath = addParam("trecEvalOutputPath");
    retrievalAlgorithm = addParam("retrievalAlgorithm");
    BM25_k_1 = addParam("BM25:k_1");
    BM25_b = addParam("BM25:b");
    BM25_k_3 = addParam("BM25:k_3");
    Indri_mu = addParam("Indri:mu");
    Indri_lambda = addParam("Indri:lambda");

    fb = addParam("fb");
    fbDocs = addParam("fbDocs");
    fbTerms = addParam("fbTerms");
    fbMu = addParam("fbMu");
    fbOrigWeight = addParam("fbOrigWeight");
    fbInitialRankingFile = addParam("fbInitialRankingFile");
    indexPath = addParam("indexPath");
    fbExpansionQueryFile = addParam("fbExpansionQueryFile");

    letor_trainingQueryFile = addParam("letor:trainingQueryFile");
    letor_trainingQrelsFile = addParam("letor:trainingQrelsFile");
    letor_featureDisable = addParam("letor:featureDisable");
    letor_trainingFeatureVectorsFile = addParam("letor:trainingFeatureVectorsFile");
    letor_pageRankFile = addParam("letor:pageRankFile");
    letor_testingFeatureVectorsFile = addParam("letor:testingFeatureVectorsFile");
    letor_testingDocumentScores = addParam("letor:testingDocumentScores");
    letor_svmRankLearnPath = addParam("letor:svmRankLearnPath");
    letor_svmRankParamC = addParam("letor:svmRankParamC");
    letor_svmRankModelFile = addParam("letor:svmRankModelFile");
    letor_svmRankClassifyPath = addParam("letor:svmRankClassifyPath");
  }

  private String addParam(String keyInParamMap) throws Exception {
    if (this.params.containsKey(keyInParamMap)) {
      return this.params.get(keyInParamMap);
    }
    //TODO: check missing
    return new String("null");
  }

}
