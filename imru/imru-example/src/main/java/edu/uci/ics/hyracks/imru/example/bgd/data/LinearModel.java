/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uci.ics.hyracks.imru.example.bgd.data;

import edu.uci.ics.hyracks.imru.api.IModel;

public class LinearModel implements IModel {

    private static final long serialVersionUID = 1L;
    public final int numFeatures;
    public FragmentableFloatArray weights;
    public float loss;
    public float stepSize = 1.0f;
    public float regularizationConstant = 0.5f;
    public int roundsRemaining;


    public LinearModel(int numFeatures, int numRounds) {
        this.numFeatures = numFeatures;
        this.weights = new FragmentableFloatArray(new float[numFeatures]);
        this.roundsRemaining = numRounds;
    }

}