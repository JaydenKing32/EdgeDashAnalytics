package com.example.edgedashanalytics.util.file;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

import android.util.Log;

import com.example.edgedashanalytics.model.Result;
import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.example.edgedashanalytics.util.video.analysis.InnerFrame;
import com.example.edgedashanalytics.util.video.analysis.OuterFrame;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class JsonManager {
    private static final String TAG = JsonManager.class.getSimpleName();

    private static final ObjectMapper mapper = JsonMapper.builder()
            .disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
            .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
            .build();
    private static final ObjectWriter writer = mapper.writer();
    private static final ObjectReader innerReader = mapper.readerFor(InnerFrame.class);
    private static final ObjectReader outerReader = mapper.readerFor(OuterFrame.class);

    public static void writeResultsToJson(String jsonFilePath, List<Frame> frames) {
        try {
            frames.sort(Comparator.comparingInt(o -> o.frame));
            writer.writeValue(new FileOutputStream(jsonFilePath), frames);
        } catch (Exception e) {
            Log.e(I_TAG, String.format("Failed to write results file:\n  %s", e.getMessage()));
        }
    }

    public static Result mergeResults(String parentName) {
        Instant start = Instant.now();
        String baseName = FilenameUtils.getBaseName(parentName);
        List<String> resultPaths = FileManager.getChildPaths(new File(FileManager.getSegmentResDirPath(baseName)));
        Log.v(TAG, String.format("Starting merge of results of %s", baseName));

        if (resultPaths == null) {
            return null;
        }

        String outPath = String.format("%s/%s", FileManager.getResultDirPath(), parentName);

        try {
            List<Frame> frames = FileManager.isInner(baseName) ?
                    getInnerFrames(resultPaths) : getOuterFrames(resultPaths);
            writer.writeValue(new FileOutputStream(outPath), frames);

            String time = FileManager.getDurationString(start);
            Log.d(I_TAG, String.format("Merged results of %s in %ss", baseName, time));

            return new Result(outPath);
        } catch (IOException e) {
            Log.e(TAG, String.format("Results merge error: \n%s", e.getMessage()));
        }
        return null;
    }

    private static List<Frame> getInnerFrames(List<String> resultPaths) throws IOException {
        int offset = 0;
        List<Frame> allFrames = new ArrayList<>();

        for (String resultPath : resultPaths) {
            MappingIterator<InnerFrame> map = innerReader.readValues(new FileInputStream(resultPath));
            List<InnerFrame> frames = map.readAll();

            if (frames.isEmpty()) {
                continue;
            }

            for (InnerFrame frame : frames) {
                frame.frame += offset;
            }

            offset = frames.get(frames.size() - 1).frame + 1;
            allFrames.addAll(frames);
        }

        return allFrames;
    }

    private static List<Frame> getOuterFrames(List<String> resultPaths) throws IOException {
        int offset = 0;
        List<Frame> allFrames = new ArrayList<>();

        for (String resultPath : resultPaths) {
            MappingIterator<OuterFrame> map = outerReader.readValues(new FileInputStream(resultPath));
            List<OuterFrame> frames = map.readAll();

            if (frames.isEmpty()) {
                continue;
            }

            for (OuterFrame frame : frames) {
                frame.frame += offset;
            }

            offset = frames.get(frames.size() - 1).frame + 1;
            allFrames.addAll(frames);
        }

        return allFrames;
    }

    public static String writeToString(Object object) {
        try {
            return writer.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static Object readFromString(String json, Class<?> classType) {
        try {
            return mapper.readValue(json, classType);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
