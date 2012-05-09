/*
 * Copyright 2009-2012 by The Regents of the University of California
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

package edu.uci.ics.hyracks.storage.am.lsm.common.api;

import java.util.Comparator;
import java.util.List;

import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.api.io.IIOManager;

/**
 * Provides file names for LSM on-disk components. Also cleans up invalid files.
 * 
 * There are separate methods to get file names for merge and flush because we
 * need to guarantee the correct order of on-disk components (i.e., the
 * components produced by flush are always newer than those produced by a
 * merge).
 */
public interface ILSMFileManager {
    public void createDirs();

    public FileReference createFlushFile(String relFlushFileName);

    public FileReference createMergeFile(String relMergeFileName);

    public Object getRelFlushFileName();

    public Object getRelMergeFileName(String firstFileName, String lastFileName) throws HyracksDataException;

    public String getBaseDir();

    // Deletes invalid files, and returns list of valid files from baseDir.
    // The returned valid files are correctly sorted (based on the recency of data). 
    public List<Object> cleanupAndGetValidFiles(Object lsmComponent, ILSMComponentFinalizer componentFinalizer)
            throws HyracksDataException;

    public Comparator<String> getFileNameComparator();

    public IIOManager getIOManager();
}
