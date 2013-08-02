/*
 * Copyright 2009-2013 by The Regents of the University of California
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

package edu.uci.ics.genomix.type;

public class KmerBytesWritableFactory {
    private VKmerBytesWritable kmer;

    public KmerBytesWritableFactory(int k) {
        kmer = new VKmerBytesWritable(k);
    }

    /**
     * Read Kmer from read text into bytes array e.g. AATAG will compress as
     * [0x000G, 0xATAA]
     * 
     * @param k
     * @param array
     * @param start
     */
    public VKmerBytesWritable getKmerByRead(int k, byte[] array, int start) {
        kmer.setByRead(k, array, start);
        return kmer;
    }

    /**
     * Compress Reversed Kmer into bytes array AATAG will compress as
     * [0x000A,0xATAG]
     * 
     * @param array
     * @param start
     */
    public VKmerBytesWritable getKmerByReadReverse(int k, byte[] array, int start) {
        kmer.setByReadReverse(k, array, start);
        return kmer;
    }

    /**
     * Get last kmer from kmer-chain.
     * e.g. kmerChain is AAGCTA, if k =5, it will
     * return AGCTA
     * 
     * @param k
     * @param kInChain
     * @param kmerChain
     * @return LastKmer bytes array
     */
    public VKmerBytesWritable getLastKmerFromChain(int lastK, final VKmerBytesWritable kmerChain) {
        if (lastK > kmerChain.getKmerLetterLength()) {
            return null;
        }
        if (lastK == kmerChain.getKmerLetterLength()) {
            kmer.setAsCopy(kmerChain);
            return kmer;
        }
        kmer.reset(lastK);

        /** from end to start */
        int byteInChain = kmerChain.getKmerByteLength() - 1 - (kmerChain.getKmerLetterLength() - lastK) / 4;
        int posInByteOfChain = ((kmerChain.getKmerLetterLength() - lastK) % 4) << 1; // *2
        int byteInKmer = kmer.getKmerByteLength() - 1;
        for (; byteInKmer >= 0 && byteInChain > 0; byteInKmer--, byteInChain--) {
            kmer.getBytes()[byteInKmer + kmer.getKmerOffset()] = (byte) ((0xff & kmerChain.getBytes()[byteInChain + kmerChain.getKmerOffset()]) >> posInByteOfChain);
            kmer.getBytes()[byteInKmer + kmer.getKmerOffset()] |= ((kmerChain.getBytes()[byteInChain  + kmerChain.getKmerOffset() - 1] << (8 - posInByteOfChain)));
        }

        /** last kmer byte */
        if (byteInKmer == 0) {
            kmer.getBytes()[0 + kmer.getKmerOffset()] = (byte) ((kmerChain.getBytes()[0 + kmerChain.getKmerOffset()] & 0xff) >> posInByteOfChain);
        }
        kmer.clearLeadBit();
        return kmer;
    }

    /**
     * Get first kmer from kmer-chain e.g. kmerChain is AAGCTA, if k=5, it will
     * return AAGCT
     * 
     * @param k
     * @param kInChain
     * @param kmerChain
     * @return FirstKmer bytes array
     */
    public VKmerBytesWritable getFirstKmerFromChain(int firstK, final VKmerBytesWritable kmerChain) {
        if (firstK > kmerChain.getKmerLetterLength()) {
            return null;
        }
        if (firstK == kmerChain.getKmerLetterLength()) {
            kmer.setAsCopy(kmerChain);
            return kmer;
        }
        kmer.reset(firstK);

        int i = 1;
        for (; i < kmer.getKmerByteLength(); i++) {
            kmer.getBytes()[kmer.getKmerOffset() + kmer.getKmerByteLength() - i] = kmerChain.getBytes()[kmerChain.getKmerOffset() + kmerChain.getKmerByteLength() - i];
        }
        int posInByteOfChain = (firstK % 4) << 1; // *2
        if (posInByteOfChain == 0) {
            kmer.getBytes()[0 + kmer.getKmerOffset()] = kmerChain.getBytes()[kmerChain.getKmerOffset() + kmerChain.getKmerByteLength() - i];
        } else {
            kmer.getBytes()[0 + kmer.getKmerOffset()] = (byte) (kmerChain.getBytes()[kmerChain.getKmerOffset() + kmerChain.getKmerByteLength() - i] & ((1 << posInByteOfChain) - 1));
        }
        kmer.clearLeadBit();
        return kmer;
    }

    public VKmerBytesWritable getSubKmerFromChain(int startK, int kSize, final VKmerBytesWritable kmerChain) {
        if (startK + kSize > kmerChain.getKmerLetterLength()) {
            return null;
        }
        if (startK == 0 && kSize == kmerChain.getKmerLetterLength()) {
            kmer.setAsCopy(kmerChain);
            return kmer;
        }
        kmer.reset(kSize);

        /** from end to start */
        int byteInChain = kmerChain.getKmerByteLength() - 1 - startK / 4;
        int posInByteOfChain = startK % 4 << 1; // *2
        int byteInKmer = kmer.getKmerByteLength() - 1;
        for (; byteInKmer >= 0 && byteInChain > 0; byteInKmer--, byteInChain--) {
            kmer.getBytes()[byteInKmer + kmer.getKmerOffset()] = (byte) ((0xff & kmerChain.getBytes()[byteInChain + kmerChain.getKmerOffset()]) >> posInByteOfChain);
            kmer.getBytes()[byteInKmer + kmer.getKmerOffset()] |= ((kmerChain.getBytes()[byteInChain + kmerChain.getKmerOffset() - 1] << (8 - posInByteOfChain)));
        }

        /** last kmer byte */
        if (byteInKmer == 0) {
            kmer.getBytes()[0 + kmer.getKmerOffset()] = (byte) ((kmerChain.getBytes()[0 + kmerChain.getKmerOffset()] & 0xff) >> posInByteOfChain);
        }
        kmer.clearLeadBit();
        return kmer;
    }

    /**
     * Merge kmer with next neighbor in gene-code format.
     * The k of new kmer will increase by 1
     * e.g. AAGCT merge with A => AAGCTA
     * 
     * @param k
     *            :input k of kmer
     * @param kmer
     *            : input bytes of kmer
     * @param nextCode
     *            : next neighbor in gene-code format
     * @return the merged Kmer, this K of this Kmer is k+1
     */
    public VKmerBytesWritable mergeKmerWithNextCode(final VKmerBytesWritable kmer, byte nextCode) {
        this.kmer.reset(kmer.getKmerLetterLength() + 1);
        for (int i = 1; i <= kmer.getKmerByteLength(); i++) {
            this.kmer.getBytes()[this.kmer.getKmerOffset() + this.kmer.getKmerByteLength() - i] = kmer.getBytes()[kmer.getKmerOffset() + kmer.getKmerByteLength() - i];
        }
        if (this.kmer.getKmerByteLength() > kmer.getKmerByteLength()) {
            this.kmer.getBytes()[0 + kmer.getKmerOffset()] = (byte) (nextCode & 0x3);
        } else {
            this.kmer.getBytes()[0 + kmer.getKmerOffset()] = (byte) (kmer.getBytes()[0 + kmer.getKmerOffset()] | ((nextCode & 0x3) << ((kmer.getKmerLetterLength() % 4) << 1)));
        }
        this.kmer.clearLeadBit();
        return this.kmer;
    }

    /**
     * Merge kmer with previous neighbor in gene-code format.
     * The k of new kmer will increase by 1
     * e.g. AAGCT merge with A => AAAGCT
     * 
     * @param k
     *            :input k of kmer
     * @param kmer
     *            : input bytes of kmer
     * @param preCode
     *            : next neighbor in gene-code format
     * @return the merged Kmer,this K of this Kmer is k+1
     */
    public VKmerBytesWritable mergeKmerWithPreCode(final VKmerBytesWritable kmer, byte preCode) {
        this.kmer.reset(kmer.getKmerLetterLength() + 1);
        int byteInMergedKmer = 0;
        if (kmer.getKmerLetterLength() % 4 == 0) {
            this.kmer.getBytes()[0 + kmer.getKmerOffset()] = (byte) ((kmer.getBytes()[0 + kmer.getKmerOffset()] >> 6) & 0x3);
            byteInMergedKmer++;
        }
        for (int i = 0; i < kmer.getKmerByteLength() - 1; i++, byteInMergedKmer++) {
            this.kmer.getBytes()[byteInMergedKmer + kmer.getKmerOffset()] = (byte) ((kmer.getBytes()[i + kmer.getKmerOffset()] << 2) | ((kmer.getBytes()[i + kmer.getKmerOffset() + 1] >> 6) & 0x3));
        }
        this.kmer.getBytes()[byteInMergedKmer + kmer.getKmerOffset()] = (byte) ((kmer.getBytes()[kmer.getKmerOffset() + kmer.getKmerByteLength() - 1] << 2) | (preCode & 0x3));
        this.kmer.clearLeadBit();
        return this.kmer;
    }

    /**
     * Merge two kmer to one kmer
     * e.g. ACTA + ACCGT => ACTAACCGT
     * 
     * @param preK
     *            : previous k of kmer
     * @param kmerPre
     *            : bytes array of previous kmer
     * @param nextK
     *            : next k of kmer
     * @param kmerNext
     *            : bytes array of next kmer
     * @return merged kmer, the new k is @preK + @nextK
     */
    public VKmerBytesWritable mergeTwoKmer(final VKmerBytesWritable preKmer, final VKmerBytesWritable nextKmer) {
        kmer.reset(preKmer.getKmerLetterLength() + nextKmer.getKmerLetterLength());
        int i = 1;
        for (; i <= preKmer.getKmerByteLength(); i++) {
            kmer.getBytes()[kmer.getKmerOffset() + kmer.getKmerByteLength() - i] = preKmer.getBytes()[preKmer.getKmerOffset() + preKmer.getKmerByteLength() - i];
        }
        if (i > 1) {
            i--;
        }
        if (preKmer.getKmerLetterLength() % 4 == 0) {
            for (int j = 1; j <= nextKmer.getKmerByteLength(); j++) {
                kmer.getBytes()[kmer.getKmerOffset() + kmer.getKmerByteLength() - i - j] = nextKmer.getBytes()[nextKmer.getKmerOffset() + nextKmer.getKmerByteLength() - j];
            }
        } else {
            int posNeedToMove = ((preKmer.getKmerLetterLength() % 4) << 1);
            kmer.getBytes()[kmer.getKmerOffset() + kmer.getKmerByteLength() - i] |= nextKmer.getBytes()[nextKmer.getKmerOffset() + nextKmer.getKmerByteLength() - 1] << posNeedToMove;
            for (int j = 1; j < nextKmer.getKmerByteLength(); j++) {
                kmer.getBytes()[kmer.getKmerOffset() + kmer.getKmerByteLength() - i - j] = (byte) (((nextKmer.getBytes()[nextKmer.getKmerOffset() + nextKmer.getKmerByteLength() - j] & 0xff) >> (8 - posNeedToMove)) | (nextKmer
                        .getBytes()[nextKmer.getKmerOffset() + nextKmer.getKmerByteLength() - j - 1] << posNeedToMove));
            }
            if (nextKmer.getKmerLetterLength() % 4 == 0 || (nextKmer.getKmerLetterLength() % 4) * 2 + posNeedToMove > 8) {
                kmer.getBytes()[0 + kmer.getKmerOffset()] = (byte) ((0xff & nextKmer.getBytes()[0 + nextKmer.getKmerOffset()]) >> (8 - posNeedToMove));
            }
        }
        kmer.clearLeadBit();
        return kmer;
    }

    /**
     * Safely shifted the kmer forward without change the input kmer
     * e.g. AGCGC shift with T => GCGCT
     * 
     * @param k
     *            : kmer length
     * @param kmer
     *            : input kmer
     * @param afterCode
     *            : input genecode
     * @return new created kmer that shifted by afterCode, the K will not change
     */
    public VKmerBytesWritable shiftKmerWithNextCode(final VKmerBytesWritable kmer, byte afterCode) {
        this.kmer.setAsCopy(kmer);
        this.kmer.shiftKmerWithNextCode(afterCode);
        return this.kmer;
    }

    /**
     * Safely shifted the kmer backward without change the input kmer
     * e.g. AGCGC shift with T => TAGCG
     * 
     * @param k
     *            : kmer length
     * @param kmer
     *            : input kmer
     * @param preCode
     *            : input genecode
     * @return new created kmer that shifted by preCode, the K will not change
     */
    public VKmerBytesWritable shiftKmerWithPreCode(final VKmerBytesWritable kmer, byte preCode) {
        this.kmer.setAsCopy(kmer);
        this.kmer.shiftKmerWithPreCode(preCode);
        return this.kmer;
    }

    /**
     * get the reverse sequence of given kmer
     * 
     * @param kmer
     */
    public VKmerBytesWritable reverse(final VKmerBytesWritable kmer) {
        this.kmer.reset(kmer.getKmerLetterLength());

        int curPosAtKmer = ((kmer.getKmerLetterLength() - 1) % 4) << 1;
        int curByteAtKmer = 0;

        int curPosAtReverse = 0;
        int curByteAtReverse = this.kmer.getKmerByteLength() - 1;
        this.kmer.getBytes()[curByteAtReverse + this.kmer.getKmerOffset()] = 0;
        for (int i = 0; i < kmer.getKmerLetterLength(); i++) {
            byte gene = (byte) ((kmer.getBytes()[curByteAtKmer + kmer.getKmerOffset()] >> curPosAtKmer) & 0x03);
            this.kmer.getBytes()[curByteAtReverse + this.kmer.getKmerOffset()] |= gene << curPosAtReverse;
            curPosAtReverse += 2;
            if (curPosAtReverse >= 8) {
                curPosAtReverse = 0;
                this.kmer.getBytes()[--curByteAtReverse + this.kmer.getKmerOffset()] = 0;
            }
            curPosAtKmer -= 2;
            if (curPosAtKmer < 0) {
                curPosAtKmer = 6;
                curByteAtKmer++;
            }
        }
        this.kmer.clearLeadBit();
        return this.kmer;
    }
}