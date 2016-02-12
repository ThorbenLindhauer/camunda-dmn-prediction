/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.slacktime;

import java.util.Map;

import com.github.thorbenlindhauer.variable.Scope;

// TODO: This and VariableIndex could go as a utility into the graphical model library
public class ProbabilityTableBuilder {

  protected Scope scope;
  protected double[] values;
  protected VariableIndex variableIndex;


  public ProbabilityTableBuilder(Scope scope, VariableIndex variableIndex) {
    this.scope = scope;
    values = new double[scope.getNumDistinctValues()];
    this.variableIndex = variableIndex;
  }

  public void submitValue(Map<String, String> variableAssignment, double value) {
    int[] assignment = new int[scope.size()];
    String[] variableIds = scope.getVariableIds();
    for (int i = 0; i < assignment.length; i++) {
      String variable = variableIds[i];
      String assignedValue = variableAssignment.get(variable);
      int valueIndex = variableIndex.getIndex(variable, assignedValue);
      assignment[i] = valueIndex;
    }

    int valueIndex = scope.getIndexCoder().getIndexForAssignment(assignment);
    values[valueIndex] = value;
  }

  public double[] getTable() {
    return values;
  }
}
