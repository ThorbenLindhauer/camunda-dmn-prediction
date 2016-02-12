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

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Thorben Lindhauer
 *
 */
public class VariableIndex {

  protected Map<String, List<String>> sortedVariableValues;

  public VariableIndex(Map<String, List<String>> sortedVariableValues) {
    this.sortedVariableValues = sortedVariableValues;
  }

  public int getIndex(String variable, String value) {
    return sortedVariableValues.get(variable).indexOf(value);
  }

  public Collection<String> getVariableValues(String variable) {
    return sortedVariableValues.get(variable);
  }

  public int getCardinality(String variable) {
    return sortedVariableValues.size();
  }
}
