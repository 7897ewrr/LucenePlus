/**
 * All query operators return QryResult objects.  QryResult objects
 * encapsulate the inverted lists (InvList) produced by qryop.QryopIl query
 * operators and the score lists (ScoreList) produced by qryop.QryopSl
 * query operators.  qryop.QryopIl query operators populate the
 * invertedList and and leave the docScores empty.  qryop.QryopSl query
 * operators leave the invertedList empty and populate the docScores.
 * Encapsulating the two types of qryop.qryop results in a single class
 * makes it easy to build structured queries with nested query
 * operators.
 * Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

package luceneplus;

import util.InvList;
import util.ScoreList;

public class QryResult {
  // Store the results of different types of query operators.
  public ScoreList docScores = new ScoreList();
  public InvList invertedList = new InvList();

}
