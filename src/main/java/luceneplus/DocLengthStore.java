package luceneplus;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiDocValues;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.NumericDocValues;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * DocLengthStore is used to access the document lengths of indexed docs.
 */
public class DocLengthStore {

  private IndexReader reader;
  private Map<String, NumericDocValues> values = new HashMap<String, NumericDocValues>();

  /**
   * @param reader IndexReader object created in {@link QryEval}.
   */
  public DocLengthStore(IndexReader reader) throws IOException {
    this.reader = reader;
    for (String field : MultiFields.getIndexedFields(reader)) {
      this.values.put(field, MultiDocValues.getNormValues(reader, field));
    }
  }

  /**
   * Returns the length of the specified field in the specified document.
   *
   * @param fieldName Name of field to access lengths. "body" is the default
   *                  field.
   * @param docId     The internal docId in the lucene index.
   */
  public long getDocLength(String fieldName, int docId) throws IOException {
    return values.get(fieldName).get(docId);
  }
}
