package util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ScoreList {

  //  A  utilty class to create a <docId, score> object.


  protected class ScoreListEntry {
    private int docId;
    private double score;

    private ScoreListEntry(int docId, double score) {
      this.docId = docId;
      this.score = score;
    }
  }


  public List<ScoreListEntry> scores = new ArrayList<ScoreListEntry>();

  /**
   * Append a document score to a score list.
   *
   * @param docId An internal document id.
   * @param score The document's score.
   * @return void
   */
  public void add(int docId, double score) {
    scores.add(new ScoreListEntry(docId, score));
  }

  /**
   * Get the n'th document id.
   *
   * @param n The index of the requested document.
   * @return The internal document id.
   */
  public int getDocId(int n) {
    return this.scores.get(n).docId;
  }

  /**
   * Get the score of the n'th document.
   *
   * @param n The index of the requested document score.
   * @return The document's score.
   */
  public double getDocIdScore(int n) {
    return this.scores.get(n).score;
  }

  public void sort() {
    Collections.sort(scores, new Comparator<ScoreList.ScoreListEntry>() {
      @Override
      public int compare(ScoreList.ScoreListEntry entry1, ScoreList.ScoreListEntry entry2) {
        if (entry1.score != entry2.score)
          return (int) (entry2.score - entry1.score);
        else
          return entry1.docId - entry2.docId;

      }
    });
  }

}
