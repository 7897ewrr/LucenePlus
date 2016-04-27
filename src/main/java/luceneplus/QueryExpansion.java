package luceneplus;

import java.io.IOException;
import java.util.*;

/**
 * Created by Tony on 11/03/14.
 *
 * Implementation of Query Expansion
 */
public class QueryExpansion {
  HashMap<Integer, Double> docScore;
  HashMap<String, Double> termScore = new HashMap<String, Double>();
  HashMap<String, Integer> termCtf = new HashMap<String, Integer>();
  ArrayList<String> l = new ArrayList<String>();
  int fbMu;
  int cLength;
  int termNum;
  DocLengthStore dls;

  QueryExpansion(HashMap<Integer, Double> docScore, int fbMu, int termNum, DocLengthStore dls) {
    this.docScore = docScore;
    this.fbMu = fbMu;
    this.termNum = termNum;
    this.dls = dls;
    try {
      cLength = (int) QryEval.READER.getSumTotalTermFreq("body");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public String newQuery() {

    Iterator iterator = docScore.keySet().iterator();
    TermVector t = null;
    int docid;
    while (iterator.hasNext()) {
      try {
        t = new TermVector((Integer) iterator.next(), "body");
        for (int i = 0; i < t.positionsLength(); i++) {
          if (t.stemString(t.stemAt(i)) == null)
            continue;
          if (t.stemString(t.stemAt(i)).split("\\.").length > 1
              || t.stemString(t.stemAt(i)).split(",").length > 1)
            continue;
          termScore.put(t.stemString(t.stemAt(i)), 0.0);
          termCtf.put(t.stemString(t.stemAt(i)), (int) t.totalStemFreq(t.stemAt(i)));
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    Iterator iterator2 = docScore.keySet().iterator();

    while (iterator2.hasNext()) {
      docid = (Integer) iterator2.next();
      try {
        t = new TermVector(docid, "body");
        for (int i = 0; i < t.positionsLength(); i++) {
          if (t.stemString(t.stemAt(i)) == null)
            continue;
          if (t.stemString(t.stemAt(i)).split("\\.").length > 1
              || t.stemString(t.stemAt(i)).split(",").length > 1)
            continue;
          if (!l.contains(t.stemString(t.stemAt(i)))) {
            l.add(t.stemString(t.stemAt(i)));
            getScore(docid, t.stemFreq(t.stemAt(i)), t, docScore.get(docid),
                t.stemString(t.stemAt(i)));
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
      Iterator iterator3 = termScore.keySet().iterator();

      while (iterator3.hasNext()) {
        String terms = (String) iterator3.next();
        if (!l.contains(terms)) {
          getDefultScore(docid, t, docScore.get(docid), terms);
        }

      }
      l = new ArrayList<String>();


    }

    List<Map.Entry<String, Double>> infoIds =
        new ArrayList<Map.Entry<String, Double>>(termScore.entrySet());

    Collections.sort(infoIds, new Comparator<Map.Entry<String, Double>>() {
      public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
        if (o1.getValue() > o2.getValue()) {
          return -1;
        } else if (o1.getValue() < o2.getValue()) {
          return 1;
        } else
          return (o1.getKey()).toString().compareTo(o2.getKey().toString());
      }
    });
    String res = ")";
    for (int i = 0; i < infoIds.size() && i < termNum; i++) {
      String[] id = infoIds.get(i).toString().split("=");
      res = id[1] + " " + id[0] + " " + res;
    }
    return "#wand( " + res;

  }

  public void getScore(int docid, int tf, TermVector t, double oldscore, String term) {
    double finalScore;
    finalScore = oldscore * Math.log((double) cLength / termCtf.get(term));

    try {
      finalScore = finalScore * ((tf + fbMu * ((double) termCtf.get(term) / cLength)) / (
          dls.getDocLength("body", docid) + fbMu));
    } catch (IOException e) {
      e.printStackTrace();
    }
    termScore.put(term, finalScore + termScore.get(term));
  }

  public void getDefultScore(int docid, TermVector t, double oldscore, String term) {
    double finalScore;
    finalScore = oldscore * Math.log((double) cLength / termCtf.get(term));
    try {
      finalScore = finalScore * ((fbMu * ((double) termCtf.get(term) / cLength)) / (
          dls.getDocLength("body", docid) + fbMu));
    } catch (IOException e) {
      e.printStackTrace();
    }
    termScore.put(term, finalScore + termScore.get(term));
  }


}
