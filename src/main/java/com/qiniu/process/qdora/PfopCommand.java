package com.qiniu.process.qdora;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.qiniu.common.QiniuException;
import com.qiniu.config.JsonFile;
import com.qiniu.model.qdora.Avinfo;
import com.qiniu.model.qdora.VideoStream;
import com.qiniu.process.Base;
import com.qiniu.util.FileNameUtils;
import com.qiniu.util.JsonConvertUtils;
import com.qiniu.util.UrlSafeBase64;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PfopCommand extends Base {

    private MediaManager mediaManager;
    private boolean hasDuration;
    private boolean hasSize;
    private String avinfoIndex;
    private ArrayList<JsonObject> pfopConfigs;
    private Gson gson;

    public PfopCommand(String jsonPath, boolean hasDuration, boolean hasSize, String avinfoIndex, String rmPrefix,
                       String savePath, int saveIndex) throws IOException {
        super("pfopcmd", null, null, null, null, rmPrefix, savePath, saveIndex);
        this.pfopConfigs = new ArrayList<>();
        JsonFile jsonFile = new JsonFile(jsonPath);
        for (String key : jsonFile.getConfigKeys()) {
            JsonObject jsonObject = jsonFile.getElement(key).getAsJsonObject();
            List<Integer> scale = JsonConvertUtils.fromJsonArray(jsonObject.get("scale").getAsJsonArray(),
                    new TypeToken<List<Integer>>(){});
            if (scale.size() < 1) {
                throw new IOException(jsonPath + " miss the scale field in \"" + key + "\"");
            } else if (scale.size() == 1) {
                JsonArray jsonArray = new JsonArray();
                jsonArray.add(scale.get(0));
                jsonArray.add(Integer.MAX_VALUE);
                jsonObject.add("scale", jsonArray);
            }
            if (!jsonObject.keySet().contains("cmd") || !jsonObject.keySet().contains("saveas"))
                throw new IOException(jsonPath + " miss the \"cmd\" or \"saveas\" fields in \"" + key + "\"");
            else if (!jsonObject.get("saveas").getAsString().contains(":"))
                throw new IOException(jsonPath + " miss the <bucket> field of \"saveas\" field in \"" + key + "\"");
            jsonObject.addProperty("name", key);
            this.pfopConfigs.add(jsonObject);
        }
        this.mediaManager = new MediaManager();
        this.hasDuration = hasDuration;
        this.hasSize = hasSize;
        if (avinfoIndex == null || "".equals(avinfoIndex)) throw new IOException("please set the avinfoIndex.");
        else this.avinfoIndex = avinfoIndex;
        this.gson = new Gson();
    }

    public PfopCommand(String jsonPath, boolean hasDuration, boolean hasSize, String avinfoIndex, String rmPrefix,
                       String savePath) throws IOException {
        this(jsonPath, hasDuration, hasSize, avinfoIndex, rmPrefix, savePath, 0);
    }

    @SuppressWarnings("unchecked")
    public PfopCommand clone() throws CloneNotSupportedException {
        PfopCommand pfopCommand = (PfopCommand)super.clone();
        pfopCommand.mediaManager = new MediaManager();
        pfopCommand.pfopConfigs = (ArrayList<JsonObject>) pfopConfigs.clone();
        pfopCommand.gson = new Gson();
        return pfopCommand;
    }

    @Override
    protected String resultInfo(Map<String, String> line) {
        return line.get("key") + "\t" + line.get(avinfoIndex);
    }

    protected String singleResult(Map<String, String> line) throws QiniuException {
        String key;
        String info;
        Avinfo avinfo;
        StringBuilder other = new StringBuilder();
        VideoStream videoStream;
        List<Integer> scale;
        List<String> items = new ArrayList<>();
        for (JsonObject pfopConfig : pfopConfigs) {
            scale = gson.fromJson(pfopConfig.get("scale").getAsJsonArray(), new TypeToken<List<Integer>>(){}.getType());
            key = line.get("key");
            info = line.get(avinfoIndex);
            try {
                if (key == null || "".equals(key) || info == null || "".equals(info))
                    throw new IOException("key or avinfo is empty.");
                key = FileNameUtils.rmPrefix(rmPrefix, line.get("key"));
                avinfo = mediaManager.getAvinfoByJson(info);
                if (hasDuration) other.append("\t").append(Double.valueOf(avinfo.getFormat().duration));
                if (hasSize) other.append("\t").append(Long.valueOf(avinfo.getFormat().size));
                videoStream = avinfo.getVideoStream();
                if (videoStream == null) throw new Exception("videoStream is null");
                if (scale.get(0) < videoStream.width && videoStream.width <= scale.get(1)) {
                    items.add(key + "\t" + generateFopCmd(key, pfopConfig) + other.toString());
                }
            } catch (Exception e) {
                throw new QiniuException(e);
            }
        }
        return String.join("\n", items);
    }

    private String generateFopCmd(String srcKey, JsonObject pfopJson) throws IOException {
        String saveAs = pfopJson.get("saveas").getAsString();
        String saveAsKey = saveAs.substring(saveAs.indexOf(":") + 1);
        if (saveAsKey.contains("$(key)")) {
            if (saveAsKey.contains(".")) {
                String[] nameParts = saveAsKey.split("(\\$\\(key\\)|\\.)");
                saveAsKey = FileNameUtils.addPrefixAndSuffixWithExt(nameParts[0], srcKey, nameParts[1], nameParts[2]);
            } else {
                String[] nameParts = saveAsKey.split("\\$\\(key\\)");
                saveAsKey = FileNameUtils.addPrefixAndSuffixKeepExt(nameParts[0], srcKey, nameParts[1]);
            }
            saveAs = saveAs.replace(saveAs.substring(saveAs.indexOf(":") + 1), saveAsKey);
        }
        return pfopJson.get("cmd").getAsString() + "|saveas/" + UrlSafeBase64.encodeToString(saveAs);
    }
}
