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
package org.camunda.bpm.slacktime.cockpit;

import java.util.Collections;
import java.util.Set;

import org.camunda.bpm.cockpit.plugin.spi.impl.AbstractCockpitPlugin;

/**
 * @author Thorben Lindhauer
 *
 */
public class DecisionPredictionCockpitPlugin extends AbstractCockpitPlugin {

  public static final String ID = "decisionPrediction";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public Set<Class<?>> getResourceClasses() {
    return Collections.<Class<?>>singleton(DecisionPredictionPluginRootResource.class);
  }

}
