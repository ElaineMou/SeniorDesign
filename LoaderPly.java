package com.projecttango.examples.java.modelcorrespondence;

/**
 * Created by Elaine on 1/21/2017.
 */

/**
 * Copyright 2013 Dennis Ippel
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.util.Log;

import org.rajawali3d.loader.AMeshLoader;
import org.rajawali3d.loader.ParsingException;
import org.rajawali3d.materials.textures.TextureManager;
import org.rajawali3d.renderer.Renderer;
import org.rajawali3d.util.LittleEndianDataInputStream;
import org.rajawali3d.util.RajLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * PLY Parser written using the ASCII format as describe on Wikipedia.
 * <p>
 *
 * @author Ian Thomas (toxicbakery@gmail.com), Elaine Mou
 *
 * @see <a href="http://en.wikipedia.org/wiki/STL_(file_format)">http://en.wikipedia.org/wiki/PLY_(file_format)</a>
 */
public class LoaderPly extends AMeshLoader {

    public enum PlyType {
        UNKNOWN,
        ASCII,
        BINARY
    }

    public LoaderPly(Renderer renderer, File file) {
        super(renderer, file);
    }

    public LoaderPly(Resources resources, TextureManager textureManager, int resourceId) {
        super(resources, textureManager, resourceId);
    }

    public LoaderPly(Renderer renderer, String fileOnSDCard) {
        super(renderer, fileOnSDCard);
    }

    @Override
    public AMeshLoader parse() throws ParsingException {
        return parse(PlyType.UNKNOWN);
    }

    public AMeshLoader parse(PlyType type) throws ParsingException {
        super.parse();
        try {

            // Open the file
            BufferedReader buffer = null;
            LittleEndianDataInputStream dis = null;

            switch (type) {
                case UNKNOWN:
                    buffer = getBufferedReader();
                    // Determine if ASCII or Binary
                    boolean isASCII = isASCII(buffer);

                    // Determine ASCII or Binary format
                    if (isASCII) {
                        readASCII(buffer);
                    } else {

                        // Switch to a LittleEndianDataInputStream (all values in binary are stored in little endian format)
                        buffer.close();
                        dis = getLittleEndianInputStream();
                        readBinary(dis);
                    }
                    break;
                case ASCII:
                    buffer = getBufferedReader();
                    readASCII(buffer);
                    break;
                case BINARY:
                    dis = getLittleEndianInputStream();
                    readBinary(dis);
                    break;
            }

            // Cleanup
            if (buffer != null)
                buffer.close();
            if (dis != null)
                dis.close();

        } catch (FileNotFoundException e) {
            RajLog.e("[" + getClass().getCanonicalName() + "] Could not find file.");
            throw new ParsingException("File not found.", e);
        } catch (NumberFormatException e) {
            RajLog.e(e.getMessage());
            throw new ParsingException("Unexpected value.", e);
        } catch (IOException e) {
            RajLog.e(e.getMessage());
            throw new ParsingException("File reading failed.", e);
        } catch (Exception e) {
            RajLog.e(e.getMessage());
            throw new ParsingException("Exception thrown.", e);
        }

        return this;
    }

    /**
     * Read stream as ASCII. While this works well, ASCII is painfully slow on mobile devices due to tight memory
     * constraints and lower processing power compared to desktops. It is advisable to use the binary parser whenever
     * possible.
     *
     * @param buffer
     * @throws NumberFormatException
     * @throws IOException
     */
    private void readASCII(final BufferedReader buffer) throws NumberFormatException, IOException {
        RajLog.i("PlyParser: Reading ASCII");
        String line;
        // Skip the first line, which is 'ply'
        buffer.readLine();
        // Skip second line, which is format
        buffer.readLine();
        ArrayList<HashMap<String, Integer>> propertiesList = new ArrayList<HashMap<String, Integer>>();
        ArrayList<Integer> counts = new ArrayList<Integer>();
        ArrayList<String> elements = new ArrayList<String>();
        int propertyIndex = 0;

        while((line = buffer.readLine()) != null && !line.contains("end_header")) {
            if (line.startsWith("comment")) {
                continue;
            }
            if (line.startsWith("element")) {
                propertiesList.add(new HashMap<String, Integer>());
                String[] args = line.split(" ");
                elements.add(args[1]);
                counts.add(Integer.parseInt(args[2]));
                propertyIndex = 0;
            } else if (line.startsWith("property")) {
                HashMap<String, Integer> properties = propertiesList.get(propertiesList.size()-1);
                String[] args = line.split(" ");
                if (args.length == 3) {
                    properties.put(args[2], propertyIndex);
                } else if (line.contains("list")) {
                    properties.put(args[4], propertyIndex);
                }
                propertyIndex++;
            }
        }
        if (elements.isEmpty()) { // nothing read from header
            return;
        }
        final float[] verticesXyz = new float[counts.get(0)*3];
        final float[] faces = new float[counts.get(1)*3*3];
        final float[] colorsRgb = new float[counts.get(0)*3];
        final float[] faceColors = new float[counts.get(1)*3*4];

        int elementIndex = 0;
        int currentCount = 0;
        HashMap<String, Integer> properties = propertiesList.get(elementIndex);
        // Read each type of element
        while ((line = buffer.readLine()) != null && elementIndex < counts.size()) {
            if (elementIndex==0) { // vertices
                final String[] COORDS = {"x","y","z"};
                final String[] COLORS = {"red","green","blue"};

                String[] stats = line.split(" ");
                for(int i=0;i<3;i++) { // should definitely have x,y,z
                    if (properties.containsKey(COORDS[i])) {
                        int index = properties.get(COORDS[i]);
                        verticesXyz[currentCount*3 + i] = Float.parseFloat(stats[index]);
                    } else {
                        verticesXyz[currentCount*3 + i] = 1.0f;
                    }
                }
                if (stats.length > 3) { // if we include colors
                    for(int i=0;i<3;i++) {
                        if (properties.containsKey(COLORS[i])) {
                            int index = properties.get(COLORS[i]);
                            colorsRgb[currentCount*3 + i]  = Float.parseFloat(stats[index]);
                        } else {
                            colorsRgb[currentCount*3 + i] = (float) 0xff; // if no colors, just do white
                        }
                    }
                }
            } else if (elementIndex==1) { // faces
                String[] numbers = line.split(" ");
                for(int j=0;j<3;j++) { // for each vertex on the triangle
                    int index = Integer.parseInt(numbers[j + 1]);
                    faces[currentCount*9 + j*3] = verticesXyz[index*3]; // add x
                    faces[currentCount*9 + j*3 + 1] = verticesXyz[index*3 + 1]; // add y
                    faces[currentCount*9 + j*3 + 2] = verticesXyz[index*3 + 2]; // add z

                    faceColors[currentCount*3*4 + j*3] = colorsRgb[index*3]; // add r
                    faceColors[currentCount*3*4 + j*3 + 1] = colorsRgb[index*3+1]; // add g
                    faceColors[currentCount*3*4 + j*3 + 2] = colorsRgb[index*3+2]; // add b
                    faceColors[currentCount*3*4 + j*3 + 3] = (float) 0xff; // add a
                }
            }

            currentCount++;
            if (currentCount == counts.get(elementIndex)) {
                elementIndex++;
                if (elementIndex < counts.size()) {
                    currentCount = 0;
                    properties = propertiesList.get(elementIndex);
                }
            }
        }

        //Log.d("PLYPARSER",colors.toString());

        int[] indicesArr = new int[verticesXyz.length / 3];
        for (int i = 0; i < indicesArr.length; i++) {
            indicesArr[i] = i;
        }

        mRootObject.setData(verticesXyz, null, null, faceColors, indicesArr, false);
    }

    /**
     * Read stream as binary STL. This is significantly faster than ASCII parsing. Additionally binary files are much
     * more compressed allowing smaller file sizes for larger models compared to ASCII.
     *
     * @param dis
     * @throws IOException
     */
    private void readBinary(final LittleEndianDataInputStream dis) throws IOException {
        RajLog.i("PlyParser: Reading Binary");

        // Skip the header
        dis.skip(80);

        // Read the number of facets (have to convert the uint to a long
        int facetCount = dis.readInt();

        float[] verticesArr = new float[facetCount * 9];
        float[] normalsArr = new float[facetCount * 9];
        int[] indicesArr = new int[facetCount * 3];
        float[] tempNorms = new float[3];
        int vertPos = 0, normPos = 0;

        for (int i = 0; i < indicesArr.length; i++)
            indicesArr[i] = i;

        // Read all the facets
        while (dis.available() > 0) {

            // Read normals
            for (int j = 0; j < 3; j++) {
                tempNorms[j] = dis.readFloat();
                if (Float.isNaN(tempNorms[j]) || Float.isInfinite(tempNorms[j])) {
                    RajLog.w("STL contains bad normals of NaN or Infinite!");
                    tempNorms[0] = 0;
                    tempNorms[1] = 0;
                    tempNorms[2] = 0;
                    break;
                }
            }

            for (int j = 0; j < 3; j++) {
                normalsArr[normPos++] = tempNorms[0];
                normalsArr[normPos++] = tempNorms[1];
                normalsArr[normPos++] = tempNorms[2];
            }

            // Read vertices
            for (int j = 0; j < 9; j++)
                verticesArr[vertPos++] = dis.readFloat();

            dis.skip(2);
        }

        mRootObject.setData(verticesArr, normalsArr, null, null, indicesArr, false);
    }

    /**
     * Determine if a given file appears to be in ASCII format.
     *
     * @param file
     * @return
     * @throws IOException
     * @throws PlyParseException
     */
    public static final boolean isASCII(File file) throws IOException, PlyParseException {
        if (file.exists())
            throw new PlyParseException("Passed file does not exist.");

        if (!file.isFile())
            throw new PlyParseException("This is not a file.");

        final BufferedReader buffer = new BufferedReader(new FileReader(file));
        boolean isASCII = isASCII(buffer);
        buffer.close();

        return isASCII;
    }

    /**
     * Determine if a given resource appears to be in ASCII format.
     *
     * @param res
     * @param resId
     * @return
     * @throws IOException
     */
    public static final boolean isASCII(Resources res, int resId) throws IOException, NotFoundException {
        BufferedReader buffer = new BufferedReader(new InputStreamReader(res.openRawResource(resId)));
        boolean isASCII = isASCII(buffer);
        buffer.close();

        return isASCII;
    }

    /**
     * Determine if a given BufferedReader appears to be in ASCII format.
     *
     * @param buffer
     * @return
     * @throws IOException
     */
    public static final boolean isASCII(BufferedReader buffer) throws IOException {
        final char[] readAhead = new char[300];
        buffer.mark(readAhead.length);
        buffer.read(readAhead, 0, readAhead.length);
        buffer.reset();
        final String readAheadString = new String(readAhead);

        // If the following text is present, then this is likely an ascii file
        if (readAheadString.contains("format ascii") && !readAheadString.contains("format binary_"))
            return true;

        // Likely a binary file
        return false;
    }

    public static final class PlyParseException extends ParsingException {
        public PlyParseException(String msg) {
            super(msg);
        }
    }

}