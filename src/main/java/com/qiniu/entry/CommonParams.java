package com.qiniu.entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.qiniu.config.JsonFile;
import com.qiniu.config.ParamsConfig;
import com.qiniu.convert.LineToMap;
import com.qiniu.interfaces.IEntryParam;
import com.qiniu.interfaces.ITypeConvert;
import com.qiniu.process.filtration.BaseFilter;
import com.qiniu.process.filtration.SeniorFilter;
import com.qiniu.util.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Base64;

public class CommonParams {

    private IEntryParam entryParam;
    private int connectTimeout;
    private int readTimeout;
    private int requestTimeout;
    private String path;
    private String source;
    private boolean isStorageSource;
    private Map<String, String> accountMap;
    private String account;
    private String qiniuAccessKey;
    private String qiniuSecretKey;
    private String tencentSecretId;
    private String tencentSecretKey;
    private String aliyunAccessId;
    private String aliyunAccessSecret;
    private String upyunUsername;
    private String upyunPassword;
    private String s3AccessId;
    private String s3SecretKey;
    private String huaweiAccessId;
    private String huaweiSecretKey;
    private String baiduAccessId;
    private String baiduSecretKey;
    private String bucket;
    private Map<String, Map<String, String>> prefixesMap;
    private List<String> antiPrefixes;
    private boolean prefixLeft;
    private boolean prefixRight;
    private String parse;
    private String separator;
    private String addKeyPrefix;
    private String rmKeyPrefix;
    private Map<String, String> linesMap;
    private String process;
    private String privateType;
    private String regionName;
    private BaseFilter<Map<String, String>> baseFilter;
    private SeniorFilter<Map<String, String>> seniorFilter;
    private Map<String, String> indexMap;
    private int unitLen;
    private int threads;
    private int batchSize;
    private int retryTimes;
    private boolean saveTotal;
    private String savePath;
    private String saveTag;
    private String saveFormat;
    private String saveSeparator;
    private List<String> rmFields;
    private Map<String, String> mapLine;
    private List<JsonObject> pfopConfigs;
    private Base64.Decoder decoder = Base64.getDecoder();
    private LocalDateTime startDateTime;
    private long pauseDelay;
    private long pauseDuration;

    public static Set<String> lineFormats = new HashSet<String>(){{
        add("csv");
        add("tab");
        add("json");
    }};

    public CommonParams() {}

    /**
     * 从入口中解析出程序运行所需要的参数，参数解析需要一定的顺序，因为部分参数会依赖前面参数解析的结果
     * @param entryParam 配置参数入口
     * @throws IOException 获取一些参数失败时抛出的异常
     */
    public CommonParams(IEntryParam entryParam) throws Exception {
        this.entryParam = entryParam;
        setTimeout();
        path = entryParam.getValue("path", "");
        setSource();
        String filePath = FileUtils.realPathWithUserHome("~" + FileUtils.pathSeparator + ".qsuits.account");
        try {
            accountMap = ParamsUtils.toParamsMap(filePath);
        } catch (FileNotFoundException ignored) {
            accountMap = new HashMap<>();
        }
        account = entryParam.getValue("a", null);
        if (account == null) {
            if (entryParam.getValue("default", "false").equals("true")) {
                account = accountMap.get("account");
                if (account == null) throw new IOException("no default account.");
            }
        }
        if (isStorageSource) {
            setAuthKey();
            setBucket();
            String prefixes = entryParam.getValue("prefixes", null);
            setPrefixesMap(entryParam.getValue("prefix-config", ""), prefixes);
            antiPrefixes = Arrays.asList(ParamsUtils.escapeSplit(entryParam.getValue("anti-prefixes", "")));
            setPrefixLeft(entryParam.getValue("prefix-left", "false").trim());
            setPrefixRight(entryParam.getValue("prefix-right", "false").trim());
        } else {
            setParse();
            setSeparator();
            addKeyPrefix = entryParam.getValue("add-keyPrefix", null);
            rmKeyPrefix = entryParam.getValue("rm-keyPrefix", null);
            setLinesMap(entryParam.getValue("line-config", ""));
        }
        setProcess();
        setPrivateType();
        regionName = entryParam.getValue("region", "").trim().toLowerCase();
        setBaseFilter();
        setSeniorFilter();
        setIndexMap();
        setUnitLen(entryParam.getValue("unit-len", "-1").trim());
        setThreads(entryParam.getValue("threads", "50").trim());
        setBatchSize(entryParam.getValue("batch-size", "-1").trim());
        setRetryTimes(entryParam.getValue("retry-times", "5").trim());
        setSaveTotal(entryParam.getValue("save-total", "").trim());
        setSavePath();
        saveTag = entryParam.getValue("save-tag", "").trim();
        saveFormat = entryParam.getValue("save-format", "tab").trim();
        saveFormat = ParamsUtils.checked(saveFormat, "save-format", "(csv|tab|json)");
        setSaveSeparator();
        setRmFields();
        setPfopConfigs();
        setStartAndPause();
    }

    public CommonParams(Map<String, String> paramsMap) throws IOException {
        this.entryParam = new ParamsConfig(paramsMap);
        setTimeout();
        source = "terminal";
        String filePath = FileUtils.realPathWithUserHome("~" + FileUtils.pathSeparator + ".qsuits.account");
        accountMap = ParamsUtils.toParamsMap(filePath);
        account = entryParam.getValue("a", null);
        if (account == null) {
            if (entryParam.getValue("default", "false").equals("true")) {
                account = accountMap.get("account");
                if (account == null) throw new IOException("no default account.");
            }
        }
        setParse();
        setSeparator();
        addKeyPrefix = entryParam.getValue("add-keyPrefix", null);
        rmKeyPrefix = entryParam.getValue("rm-keyPrefix", null);
        setProcess();
        regionName = entryParam.getValue("region", "").trim().toLowerCase();
        setIndexMap();
        setRetryTimes(entryParam.getValue("retry-times", "5").trim());
        String line = entryParam.getValue("line", null);
        ITypeConvert<String, Map<String, String>> converter = new LineToMap(parse, separator, addKeyPrefix, rmKeyPrefix, indexMap);
        boolean fromLine = line != null && !"".equals(line);
        if ((entryParam.getValue("indexes", null) != null || indexMap.size() > 1) && !fromLine) {
            throw new IOException("you have set parameter for line index but no line data to parse, please set \"-line=<data>\".");
        }
        if (fromLine) {
            mapLine = converter.convertToV(line);
            fromLine = mapLine.containsKey("key");
        } else {
            mapLine = new HashMap<>();
        }
        switch (process) {
            case "copy":
            case "move":
            case "rename":
                if (!fromLine) mapLine.put("key", entryParam.getValue("key"));
                String toKey = entryParam.getValue("to-key", null);
                if (toKey != null) {
                    indexMap.put("toKey", "toKey");
                    mapLine.put("toKey", toKey);
                }
                break;
            case "asyncfetch":
            case "avinfo":
            case "qhash":
            case "privateurl":
            case "exportts":
            case "download":
            // 这几个数据源的私有签名都是采用 bucket + key + endpoint(region) 的方式来签算
//            case "tenprivate":
//            case "aliprivate":
//            case "s3private":
//            case "awsprivate":
//            case "huaweiprivate":
//            case "baiduprivate":
            case "imagecensor":
            case "videocensor":
                String url = entryParam.getValue("url", "").trim();
                if (!"".equals(url)) {
                    indexMap.put("url", "url");
                    mapLine.put("url", url);
                    mapLine.put("key", entryParam.getValue("key", null));
                } else if (!fromLine) {
                    entryParam.getValue("domain");
                    mapLine.put("key", entryParam.getValue("key"));
                }
                break;
            case "pfop":
                if (!fromLine) mapLine.put("key", entryParam.getValue("key"));
                String fops = entryParam.getValue("fops", "").trim();
                if (!"".equals(fops)) {
                    indexMap.put("fops", "fops");
                    mapLine.put("fops", fops);
                }
                setPfopConfigs();
                break;
            case "pfopcmd":
                if (!fromLine) mapLine.put("key", entryParam.getValue("key"));
                String avinfo = entryParam.getValue("avinfo", "").trim();
                if (!"".equals(avinfo)) {
                    indexMap.put("avinfo", "avinfo");
                    mapLine.put("avinfo", avinfo);
                }
                setPfopConfigs();
                break;
            case "pfopresult":
            case "censorresult":
                String id = entryParam.getValue("id", "").trim();
                if (!"".equals(id)) {
                    indexMap.put("id", "id");
                    mapLine.put("id", id);
                }
                break;
            case "stat":
                if (!fromLine) mapLine.put("key", entryParam.getValue("key"));
                saveFormat = entryParam.getValue("save-format", "tab").trim();
                saveFormat = ParamsUtils.checked(saveFormat, "save-format", "(csv|tab|json)");
                setSaveSeparator();
                break;
            case "qupload":
                if (!fromLine) mapLine.put("key", entryParam.getValue("key"));
                String filepath = entryParam.getValue("filepath", "").trim();
                if (!"".equals(filepath)) {
                    indexMap.put("filepath", "filepath");
                    mapLine.put("filepath", filepath);
                }
                break;
            default: if (!fromLine) mapLine.put("key", entryParam.getValue("key"));
                break;
        }
    }

    private void setTimeout() {
        connectTimeout = Integer.valueOf(entryParam.getValue("connect-timeout", "60").trim());
        readTimeout = Integer.valueOf(entryParam.getValue("read-timeout", "120").trim());
        requestTimeout = Integer.valueOf(entryParam.getValue("request-timeout", "60").trim());
    }

    private void setSource() throws IOException {
        if (entryParam.getValue("interactive", "").trim().equals("true")) {
            source = "terminal";
            return;
        }
        try {
            source = entryParam.getValue("source-type").trim();
        } catch (IOException e1) {
            try {
                source = entryParam.getValue("source").trim();
            } catch (IOException e2) {
                if ("".equals(path) || path.startsWith("qiniu://")) source = "qiniu";
                else if (path.startsWith("tencent://")) source = "tencent";
                else if (path.startsWith("aliyun://")) source = "aliyun";
                else if (path.startsWith("upyun://")) source = "upyun";
                else if (path.startsWith("aws://") || path.startsWith("s3://")) source = "s3";
                else if (path.startsWith("huawei://")) source = "huawei";
                else if (path.startsWith("baidu://")) source = "baidu";
                else source = "local";
            }
        }
        // list 和 file 方式是兼容老的数据源参数，list 默认表示从七牛进行列举，file 表示从本地读取文件
        if ("list".equals(source)) source = "qiniu";
        else if ("file".equals(source)) source = "local";
        else if ("aws".equals(source)) source = "s3";
        if (!source.matches("(local|qiniu|tencent|aliyun|upyun|s3|huawei|baidu)")) {
            throw new IOException("the datasource is supported only in: [local,qiniu,tencent,aliyun,upyun,s3]");
        }
        isStorageSource = CloudApiUtils.isStorageSource(source);
    }

    private void setParse() throws IOException {
        parse = entryParam.getValue("parse", "tab").trim();
        parse = ParamsUtils.checked(parse, "parse", "(csv|tab|json)");
    }

    private void setSeparator() {
        String separator = entryParam.getValue("separator", "");
        if (separator == null || separator.isEmpty()) {
            if ("tab".equals(parse)) this.separator = "\t";
            else if ("csv".equals(parse)) this.separator = ",";
            else this.separator = " ";
        } else {
            this.separator = separator;
        }
    }

    private void setLinesMap(String linesConfig) throws Exception {
        linesMap = new HashMap<>();
        if (linesConfig != null && !"".equals(linesConfig)) {
            JsonFile jsonFile = new JsonFile(linesConfig);
            JsonElement lineCfg;
            for (String filename : jsonFile.getKeys()) {
                lineCfg = jsonFile.getElement(filename);
                if (lineCfg == null || lineCfg instanceof JsonNull) {
                    linesMap.put(filename, "");
                } else {
                    linesMap.put(filename, lineCfg.getAsString());
                }
            }
        }
    }

    private void setQiniuAuthKey() throws IOException {
        if (account == null) {
            qiniuAccessKey = entryParam.getValue("ak").trim();
            qiniuSecretKey = entryParam.getValue("sk").trim();
        } else {
            qiniuAccessKey = accountMap.get(account + "-qiniu-id");
            qiniuSecretKey = accountMap.get(account + "-qiniu-secret");
            if (qiniuAccessKey == null || qiniuSecretKey == null) throw new IOException("no account: " + account);
            qiniuAccessKey = new String(decoder.decode(qiniuAccessKey.substring(8)));
            qiniuSecretKey = new String(decoder.decode(qiniuSecretKey.substring(8)));
        }
    }

    private void setTencentAuthKey() throws IOException {
        if (account == null) {
            tencentSecretId = entryParam.getValue("ten-id").trim();
            tencentSecretKey = entryParam.getValue("ten-secret").trim();
        } else {
            tencentSecretId = accountMap.get(account + "-tencent-id");
            tencentSecretKey = accountMap.get(account + "-tencent-secret");
            if (tencentSecretId == null || tencentSecretKey == null) throw new IOException("no account: " + account);
            tencentSecretId = new String(decoder.decode(tencentSecretId.substring(8)));
            tencentSecretKey = new String(decoder.decode(tencentSecretKey.substring(8)));
        }
    }

    private void setAliyunAuthKey() throws IOException {
        if (account == null) {
            aliyunAccessId = entryParam.getValue("ali-id").trim();
            aliyunAccessSecret = entryParam.getValue("ali-secret").trim();
        } else {
            aliyunAccessId = accountMap.get(account + "-aliyun-id");
            aliyunAccessSecret = accountMap.get(account + "-aliyun-secret");
            if (aliyunAccessId == null || aliyunAccessSecret == null) throw new IOException("no account: " + account);
            aliyunAccessId = new String(decoder.decode(aliyunAccessId.substring(8)));
            aliyunAccessSecret = new String(decoder.decode(aliyunAccessSecret.substring(8)));
        }
    }

    private void setUpyunAuthKey() throws IOException {
        if (account == null) {
            upyunUsername = entryParam.getValue("up-id").trim();
            upyunPassword = entryParam.getValue("up-secret").trim();
        } else {
            upyunUsername = accountMap.get(account + "-upyun-id");
            upyunPassword = accountMap.get(account + "-upyun-secret");
            if (upyunUsername == null || upyunPassword == null) throw new IOException("no account: " + account);
            upyunUsername = new String(decoder.decode(upyunUsername.substring(8)));
            upyunPassword = new String(decoder.decode(upyunPassword.substring(8)));
        }
    }

    private void setS3AuthKey() throws IOException {
        if (account == null) {
            s3AccessId = entryParam.getValue("s3-id").trim();
            s3SecretKey = entryParam.getValue("s3-secret").trim();
        } else {
            s3AccessId = accountMap.get(account + "-s3-id");
            s3SecretKey = accountMap.get(account + "-s3-secret");
            if (s3AccessId == null || s3SecretKey == null) throw new IOException("no account: " + account);
            s3AccessId = new String(decoder.decode(s3AccessId.substring(8)));
            s3SecretKey = new String(decoder.decode(s3SecretKey.substring(8)));
        }
    }

    private void setHuaweiAuthKey() throws IOException {
        if (account == null) {
            huaweiAccessId = entryParam.getValue("hua-id").trim();
            huaweiSecretKey = entryParam.getValue("hua-secret").trim();
        } else {
            huaweiAccessId = accountMap.get(account + "-huawei-id");
            huaweiSecretKey = accountMap.get(account + "-huawei-secret");
            if (huaweiAccessId == null || huaweiSecretKey == null) throw new IOException("no account: " + account);
            huaweiAccessId = new String(decoder.decode(huaweiAccessId.substring(8)));
            huaweiSecretKey = new String(decoder.decode(huaweiSecretKey.substring(8)));
        }
    }

    private void setBaiduAuthKey() throws IOException {
        if (account == null) {
            baiduAccessId = entryParam.getValue("bai-id").trim();
            baiduSecretKey = entryParam.getValue("bai-secret").trim();
        } else {
            baiduAccessId = accountMap.get(account + "-baidu-id");
            baiduSecretKey = accountMap.get(account + "-baidu-secret");
            if (baiduAccessId == null || baiduSecretKey == null) throw new IOException("no account: " + account);
            baiduAccessId = new String(decoder.decode(baiduAccessId.substring(8)));
            baiduSecretKey = new String(decoder.decode(baiduSecretKey.substring(8)));
        }
    }

    private void setAuthKey() throws IOException {
        if ("qiniu".equals(source)) {
            setQiniuAuthKey();
        } else if ("tencent".equals(source)) {
            setTencentAuthKey();
        } else if ("aliyun".equals(source)) {
            setAliyunAuthKey();
        } else if ("upyun".equals(source)) {
            setUpyunAuthKey();
        } else if ("s3".equals(source)) {
            setS3AuthKey();
        } else if ("huawei".equals(source)) {
            setHuaweiAuthKey();
        } else if ("baidu".equals(source)) {
            setBaiduAuthKey();
        } else {
            if (account == null) {
                qiniuAccessKey = entryParam.getValue("ak", "").trim();
                qiniuSecretKey = entryParam.getValue("sk", "").trim();
            } else {
                qiniuAccessKey = accountMap.get(account + "-qiniu-id");
                qiniuSecretKey = accountMap.get(account + "-qiniu-secret");
            }
        }
    }

    /**
     * 支持从路径方式上解析出 bucket，如果主动设置 bucket 则替换路径中的值
     * @throws IOException 解析 bucket 参数失败抛出异常
     */
    private void setBucket() throws IOException {
        if ("qiniu".equals(source) && path.startsWith("qiniu://")) bucket = path.substring(8);
        else if ("tencent".equals(source) && path.startsWith("tencent://")) bucket = path.substring(10);
        else if ("aliyun".equals(source) && path.startsWith("aliyun://")) bucket = path.substring(9);
        else if ("upyun".equals(source) && path.startsWith("upyun://")) bucket = path.substring(8);
        else if ("s3".equals(source)) {
            if (path.startsWith("s3://")) bucket = path.substring(5);
            else if (path.startsWith("aws://")) bucket = path.substring(6);
        }
        else if ("huawei".equals(source) && path.startsWith("huawei://")) bucket = path.substring(9);
        else if ("baidu".equals(source) && path.startsWith("baidu://")) bucket = path.substring(8);
        if (bucket == null || "".equals(bucket)) bucket = entryParam.getValue("bucket").trim();
        else bucket = entryParam.getValue("bucket", bucket).trim();
    }

    private void setProcess() throws IOException {
        process = entryParam.getValue("process", "").trim();
        if (!process.isEmpty() && isStorageSource && !ProcessUtils.supportStorageSource(process)) {
            throw new IOException("the process: " + process + " don't support getting source line from list.");
        }
        if (ProcessUtils.needQiniuAuth(process)) {
            setQiniuAuthKey();
        } else if (ProcessUtils.needTencentAuth(process)) {
            setTencentAuthKey();
        } else if (ProcessUtils.needAliyunAuth(process)) {
            setAliyunAuthKey();
        } else if (ProcessUtils.needAwsS3Auth(process)) {
            setS3AuthKey();
        } else if (ProcessUtils.needHuaweiAuth(process)) {
            setHuaweiAuthKey();
        } else if (ProcessUtils.needBaiduAuth(process)) {
            setBaiduAuthKey();
        }
        if (ProcessUtils.needBucket(process)) bucket = entryParam.getValue("bucket", bucket).trim();
    }

    private void setPrivateType() throws IOException {
        privateType = entryParam.getValue("private", "").trim();
        if ("".equals(privateType)) return;
        switch (privateType) {
            case "qiniu":
                if (!"qiniu".equals(source) && isStorageSource) {
                    throw new IOException("the privateType: " + privateType + " can not match source: " + source);
                }
                break;
            case "tencent":
                if (!"tencent".equals(source) && isStorageSource) {
                    throw new IOException("the privateType: " + privateType + " can not match source: " + source);
                }
                break;
            case "aliyun":
                if (!"aliyun".equals(source) && isStorageSource) {
                    throw new IOException("the privateType: " + privateType + " can not match source: " + source);
                }
                break;
            case "aws":
            case "s3":
                if (!"s3".equals(source) && isStorageSource) {
                    throw new IOException("the privateType: " + privateType + " can not match source: " + source);
                }
                break;
            case "huawei":
                if (!"huawei".equals(source) && isStorageSource) {
                    throw new IOException("the privateType: " + privateType + " can not match source: " + source);
                }
                break;
            case "baidu":
                if (!"baidu".equals(source) && isStorageSource) {
                    throw new IOException("the privateType: " + privateType + " can not match source: " + source);
                }
                break;
            default: throw new IOException("unsupported private-type: " + privateType);
        }
    }

    private void setPrefixesMap(String prefixConfig, String prefixes) throws Exception {
        prefixesMap = new HashMap<>();
        if (prefixConfig != null && !"".equals(prefixConfig)) {
            JsonFile jsonFile = new JsonFile(prefixConfig);
            JsonObject jsonCfg;
            JsonElement markerElement;
            JsonElement startElement;
            JsonElement endElement;
            for (String prefix : jsonFile.getKeys()) {
                Map<String, String> markerAndEnd = new HashMap<>();
//                if ("".equals(prefix)) throw new IOException("prefix (prefixes config's element key) can't be empty.");
                JsonElement json = jsonFile.getElement(prefix);
                if (json == null || json instanceof JsonNull) {
                    prefixesMap.put(prefix, null);
                    continue;
                }
                if (!(json instanceof JsonObject)) throw new IOException("the value of key: " + prefix + " must be json.");
                jsonCfg = json.getAsJsonObject();
                markerElement = jsonCfg.get("marker");
                startElement = jsonCfg.get("start");
                endElement = jsonCfg.get("end");
                if (markerElement != null && !(markerElement instanceof JsonNull)) {
                    markerAndEnd.put("marker", markerElement.getAsString());
                }
                if (startElement != null && !(startElement instanceof JsonNull)) {
                    markerAndEnd.put("start", startElement.getAsString());
                }
                if (endElement != null && !(endElement instanceof JsonNull)) {
                    markerAndEnd.put("end", endElement.getAsString());
                }
                prefixesMap.put(prefix, markerAndEnd);
            }
        } else if (prefixes != null && !"".equals(prefixes)) {
            String[] prefixList = ParamsUtils.escapeSplit(prefixes);
            for (String prefix : prefixList) prefixesMap.put(prefix, new HashMap<>());
        }
    }

    private void setPrefixLeft(String prefixLeft) throws IOException {
        this.prefixLeft = Boolean.valueOf(ParamsUtils.checked(prefixLeft, "prefix-left", "(true|false)"));
    }

    private void setPrefixRight(String prefixRight) throws IOException {
        this.prefixRight = Boolean.valueOf(ParamsUtils.checked(prefixRight, "prefix-right", "(true|false)"));
    }

    public String[] splitDateScale(String dateScale) throws IOException {
        String[] scale;
        if (dateScale != null && !"".equals(dateScale)) {
            // 设置的 dateScale 格式应该为 [yyyy-MM-dd HH:mm:ss,yyyy-MM-dd HH:mm:ss]
            if (dateScale.startsWith("[") && dateScale.endsWith("]")) {
                scale = dateScale.substring(1, dateScale.length() - 1).split(",");
            } else if (dateScale.startsWith("[") || dateScale.endsWith("]")) {
                throw new IOException("please check your date scale, set it as \"[<date1>,<date2>]\".");
            } else {
                scale = dateScale.split(",");
            }
        } else {
            scale = new String[]{null, null};
        }
        if (scale.length <= 1) {
            throw new IOException("please set start and end date, if no start please set is as \"[0,<date>]\", or " +
                    "no end please set it as \"[<date>,now/max]\"");
        }
        return scale;
    }

    public LocalDateTime checkedDatetime(String datetime) throws Exception {
        LocalDateTime dateTime;
        if (datetime == null) {
            return null;
        } else {
            datetime = datetime.trim();
        }
        if (datetime.matches("(|0)")) {
            dateTime = LocalDateTime.MIN;
        } else if (datetime.equals("now")) {
            dateTime = LocalDateTime.now();
        } else if (datetime.equals("max")) {
            dateTime = LocalDateTime.MAX;
        } else if (datetime.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            dateTime = LocalDateTime.parse(datetime.replace(" ", "T"));
        } else if (datetime.matches("\\d{4}-\\d{2}-\\d{2}")) {
            dateTime = LocalDateTime.parse(datetime + "T00:00:00");
        } else {
            throw new IOException("please check your datetime string format, set it as \"yyyy-MM-dd HH:mm:ss\".");
        }
        return dateTime;
    }

    private void setBaseFilter() throws Exception {
        String keyPrefix = entryParam.getValue("f-prefix", "");
        String keySuffix = entryParam.getValue("f-suffix", "");
        String keyInner = entryParam.getValue("f-inner", "");
        String keyRegex = entryParam.getValue("f-regex", "");
        String mimeType = entryParam.getValue("f-mime", "");
        String antiKeyPrefix = entryParam.getValue("f-anti-prefix", "");
        String antiKeySuffix = entryParam.getValue("f-anti-suffix", "");
        String antiKeyInner = entryParam.getValue("f-anti-inner", "");
        String antiKeyRegex = entryParam.getValue("f-anti-regex", "");
        String antiMimeType = entryParam.getValue("f-anti-mime", "");
        String[] dateScale = splitDateScale(entryParam.getValue("f-date-scale", "").trim());
        LocalDateTime putTimeMin = checkedDatetime(dateScale[0]);
        LocalDateTime putTimeMax = checkedDatetime(dateScale[1]);
        if (putTimeMin != null && putTimeMax != null && putTimeMax.compareTo(putTimeMin) <= 0) {
            throw new IOException("please set date scale to make first as start date, second as end date, <date1> " +
                    "should earlier then <date2>.");
        }
        String type = entryParam.getValue("f-type", "").trim();
        String status = entryParam.getValue("f-status", "").trim();
        if (!"".equals(status)) status = ParamsUtils.checked(status, "f-status", "[01]");

        List<String> keyPrefixList = Arrays.asList(ParamsUtils.escapeSplit(keyPrefix));
        List<String> keySuffixList = Arrays.asList(ParamsUtils.escapeSplit(keySuffix));
        List<String> keyInnerList = Arrays.asList(ParamsUtils.escapeSplit(keyInner));
        List<String> keyRegexList = Arrays.asList(ParamsUtils.escapeSplit(keyRegex));
        List<String> mimeTypeList = Arrays.asList(ParamsUtils.escapeSplit(mimeType));
        List<String> antiKeyPrefixList = Arrays.asList(ParamsUtils.escapeSplit(antiKeyPrefix));
        List<String> antiKeySuffixList = Arrays.asList(ParamsUtils.escapeSplit(antiKeySuffix));
        List<String> antiKeyInnerList = Arrays.asList(ParamsUtils.escapeSplit(antiKeyInner));
        List<String> antiKeyRegexList = Arrays.asList(ParamsUtils.escapeSplit(antiKeyRegex));
        List<String> antiMimeTypeList = Arrays.asList(ParamsUtils.escapeSplit(antiMimeType));
        try {
            baseFilter = new BaseFilter<Map<String, String>>(keyPrefixList, keySuffixList, keyInnerList, keyRegexList,
                    antiKeyPrefixList, antiKeySuffixList, antiKeyInnerList, antiKeyRegexList, mimeTypeList, antiMimeTypeList,
                    putTimeMin, putTimeMax, type, status) {
                @Override
                protected String valueFrom(Map<String, String> item, String key) {
                    return item.get(key);
                }
            };
        } catch (IOException e) {
            baseFilter = null;
        }
    }

    private void setSeniorFilter() throws IOException {
        String checkType = entryParam.getValue("f-check", "").trim();
        checkType = ParamsUtils.checked(checkType, "f-check", "(|ext-mime)").trim();
        String checkConfig = entryParam.getValue("f-check-config", "");
        String checkRewrite = entryParam.getValue("f-check-rewrite", "false").trim();
        checkRewrite = ParamsUtils.checked(checkRewrite, "f-check-rewrite", "(true|false)");
        try {
            seniorFilter = new SeniorFilter<Map<String, String>>(checkType, checkConfig, Boolean.valueOf(checkRewrite)) {
                @Override
                protected String valueFrom(Map<String, String> item, String key) {
                    return item.get(key);
                }
            };
        } catch (Exception e) {
            seniorFilter = null;
        }
    }

    private void setIndex(String index, String indexName) throws IOException {
        if (indexMap.containsKey(index)) {
            throw new IOException("index: " + index + " is already used by \"" + indexMap.get(index) + "-index=" + index + "\"");
        }
        if (index != null && !"".equals(index) && !"-1".equals(index)) {
            if ("tab".equals(parse) || "csv".equals(parse)) {
                if (index.matches("\\d+")) {
                    indexMap.put(index, indexName);
                } else {
                    throw new IOException("incorrect " + indexName + "-index: " + index + ", it should be a number.");
                }
            } else if (parse == null || "json".equals(parse) || "".equals(parse) || "object".equals(parse)) {
                indexMap.put(index, indexName);
            } else {
                throw new IOException("the parse type: " + parse + " is unsupported now.");
            }
        }
    }

    private void setIndexMap() throws IOException {
        indexMap = new HashMap<>();
        List<String> keys = new ArrayList<>(ConvertingUtils.defaultFileFields);
        int fieldsMode = 0;
        if ("upyun".equals(source)) {
            fieldsMode = 1;
            keys.remove(ConvertingUtils.defaultEtagField);
            keys.remove(ConvertingUtils.defaultTypeField);
            keys.remove(ConvertingUtils.defaultStatusField);
            keys.remove(ConvertingUtils.defaultMd5Field);
            keys.remove(ConvertingUtils.defaultOwnerField);
        } else if ("huawei".equals(source)) {
            fieldsMode = 2;
            keys.remove(ConvertingUtils.defaultStatusField);
        } else if (isStorageSource && !"qiniu".equals(source)) {
            fieldsMode = 3;
            keys.remove(ConvertingUtils.defaultMimeField);
            keys.remove(ConvertingUtils.defaultStatusField);
            keys.remove(ConvertingUtils.defaultMd5Field);
        }
        String indexes = entryParam.getValue("indexes", "").trim();
        if (indexes.startsWith("[") && indexes.endsWith("]")) {
            indexes = indexes.substring(1, indexes.length() - 1);
            String[] strings = ParamsUtils.escapeSplit(indexes, false);
            for (int i = 0; i < strings.length; i++) {
                if (strings[i].matches(".+:.+")) {
                    String[] keyIndex = ParamsUtils.escapeSplit(strings[i], ':');
                    if (keyIndex.length != 2) throw new IOException("incorrect key:index pattern: " + strings[i]);
                    setIndex(keyIndex[1], keyIndex[0]);
                } else {
                    if (i >= keys.size()) throw new IOException("the index is out of default fields size.");
                    setIndex(strings[i], keys.get(i));
                }
            }
        } else if (indexes.startsWith("[") || indexes.endsWith("]")) {
            throw new IOException("please check your indexes, set it as \"[key1:index1,key2:index2,...]\".");
        } else if (!"".equals(indexes)) {
            String[] indexList = ParamsUtils.escapeSplit(indexes);
            for (int i = 0; i < indexList.length; i++) {
                if ("timestamp".equals(indexList[i])) {
                    indexMap.put(indexList[i], "timestamp");
                    keys.add(i, "timestamp");
                } else {
                    if (i >= keys.size()) {
                        throw new IOException("the file object fields' indexes are too long than default: " + keys);
                    }
                    setIndex(indexList[i], keys.get(i));
                }
            }
        }
        if (ProcessUtils.needUrl(process))
            setIndex(entryParam.getValue("url-index", "").trim(), "url");
        if (ProcessUtils.needToKey(process))
            setIndex(entryParam.getValue("toKey-index", "").trim(), "toKey");
        if (ProcessUtils.needFops(process))
            setIndex(entryParam.getValue("fops-index", "").trim(), "fops");
        if (ProcessUtils.needId(process))
            setIndex(entryParam.getValue("id-index", "").trim(), "id");
        if (ProcessUtils.needAvinfo(process))
            setIndex(entryParam.getValue("avinfo-index", "").trim(), "avinfo");
        if (ProcessUtils.needFilepath(process))
            setIndex(entryParam.getValue("filepath-index", "").trim(), "filepath");

        boolean useDefault = false;
        boolean fieldIndex = parse == null || "json".equals(parse) || "".equals(parse) || "object".equals(parse);
        if (indexMap.size() == 0) {
            useDefault = true;
            if (isStorageSource) {
                for (String key : keys) indexMap.put(key, key);
            } else if (fieldIndex) {
                indexMap.put("key", "key");
            } else {
                indexMap.put("0", "key");
            }
        }

        if (baseFilter != null) {
            if (baseFilter.checkKeyCon() && !indexMap.containsValue("key")) {
                if (useDefault) {
                    indexMap.put(fieldIndex ? "key" : "0", "key");
                } else {
                    throw new IOException("f-[x] about key filter for file key must get the key's index in indexes settings.");
                }
            }
            if (baseFilter.checkDatetimeCon() && !indexMap.containsValue("datetime")) {
                if (useDefault) {
                    indexMap.put(fieldIndex ? "datetime" : "3", "datetime");
                } else {
                    throw new IOException("f-date-scale filter must get the datetime's index in indexes settings.");
                }
            }
            if (baseFilter.checkMimeTypeCon() && !indexMap.containsValue("mime")) {
                if (useDefault) {
                    if (fieldsMode != 3) {
                        indexMap.put(fieldIndex ? "mime" : "4", "mime");
                    }
                } else {
                    throw new IOException("f-mime filter must get the mime's index in indexes settings.");
                }
            }
            if (baseFilter.checkTypeCon() && !indexMap.containsValue("type")) {
                if (useDefault) {
                    if (fieldsMode != 1) {
                        indexMap.put(fieldIndex ? "type" : "5", "type");
                    }
                } else {
                    throw new IOException("f-type filter must get the type's index in indexes settings.");
                }
            }
            if (baseFilter.checkStatusCon() && !indexMap.containsValue("status")) {
                if (useDefault) {
                    if (fieldsMode == 0) {
                        indexMap.put(fieldIndex ? "status" : "6", "status");
                    }
                } else {
                    throw new IOException("f-status filter must get the status's index in indexes settings.");
                }
            }
        }
        if (seniorFilter != null) {
            if (seniorFilter.checkExtMime()) {
                if (!indexMap.containsValue("key")) {
                    if (useDefault) {
                        indexMap.put(fieldIndex ? "key" : "0", "key");
                    } else {
                        throw new IOException("f-check=ext-mime filter must get the key's index in indexes settings.");
                    }
                }
                if (!indexMap.containsValue("mime")) {
                    if (useDefault) {
                        if (fieldsMode != 3) {
                            indexMap.put(fieldIndex ? "mime" : "4", "mime");
                        }
                    } else {
                        throw new IOException("f-check=ext-mime filter must get the mime's index in indexes settings.");
                    }
                }
            }
        }
    }

    private void setUnitLen(String unitLen) throws IOException {
        if (unitLen.startsWith("-")) {
            if ("qiniu".equals(source) || "local".equals(source)) unitLen = "10000";
            else unitLen = "1000";
        }
        this.unitLen = Integer.valueOf(ParamsUtils.checked(unitLen, "unit-len", "\\d+"));
    }

    private void setThreads(String threads) throws IOException {
        this.threads = Integer.valueOf(ParamsUtils.checked(threads, "threads", "[1-9]\\d*"));
    }

    private void setBatchSize(String batchSize) throws IOException {
        if (batchSize.startsWith("-")) {
            if (ProcessUtils.canBatch(process)) {
                batchSize = "stat".equals(process) ? "100" : "1000";
            } else {
                batchSize = "0";
            }
        }
        this.batchSize = Integer.valueOf(ParamsUtils.checked(batchSize, "batch-size", "\\d+"));
    }

    private void setRetryTimes(String retryTimes) throws IOException {
        this.retryTimes = Integer.valueOf(ParamsUtils.checked(retryTimes, "retry-times", "\\d+"));
    }

    private void setSaveTotal(String saveTotal) throws IOException {
        if (saveTotal == null || "".equals(saveTotal)) {
            if (isStorageSource) {
                saveTotal = "true";

//（2）云存储数据源时如果无 process 则为 true，如果存在 process 且包含 filter 设置时为 false，既存在 process 同时包含 filter 设置时为 true。
//                if (process == null || "".equals(process)) {
//                    saveTotal = "true";
//                } else {
//                    if (baseFilter != null || seniorFilter != null) saveTotal = "true";
//                    else saveTotal = "false";
//                }
            } else {
                if ((process != null && !"".equals(process)) || baseFilter != null || seniorFilter != null) saveTotal = "false";
                else saveTotal = "true";
            }
        }
        this.saveTotal = Boolean.valueOf(ParamsUtils.checked(saveTotal, "save-total", "(true|false)"));
    }

    private void setSavePath() {
        savePath = entryParam.getValue("save-path", "local".equals(source) ? (path.endsWith("/") ?
                path.substring(0, path.length() - 1) : path) + "-result" : bucket);
    }

    private void setSaveSeparator() {
        String separator = entryParam.getValue("save-separator", "");
        if (separator == null || separator.isEmpty()) {
            if ("tab".equals(saveFormat)) this.saveSeparator = "\t";
            else if ("csv".equals(saveFormat)) this.saveSeparator = ",";
            else this.saveSeparator = " ";
        } else {
            this.saveSeparator = separator;
        }
    }

    private void setRmFields() throws IOException {
        String param = entryParam.getValue("rm-fields", "").trim();
        if ("".equals(param)) {
            rmFields = null;
        } else {
            String[] fields = ParamsUtils.escapeSplit(param);
            rmFields = new ArrayList<>();
            Collections.addAll(rmFields, fields);
        }
    }

    private void setPfopConfigs() throws IOException {
        String cmd = entryParam.getValue("cmd", "").trim();
        if (!"".equals(cmd)) {
            JsonObject pfopJson = new JsonObject();
            pfopJson.addProperty("cmd", cmd);
            String saveas = entryParam.getValue("saveas");
            pfopJson.addProperty("saveas", saveas);
            if ("pfopcmd".equals(process)) {
                String scale = entryParam.getValue("scale").trim();
                if (!scale.matches("\\[.*]")) throw new IOException("correct \"scale\" parameter should " +
                        "like \"[num1,num2]\"");
                String[] scales = scale.substring(1, scale.length() - 1).split(",");
                JsonArray jsonArray = new JsonArray();
                if (scales.length > 1) {
                    jsonArray.add(scales[0]);
                    jsonArray.add(scales[1]);
                } else {
                    jsonArray.add(Integer.valueOf(scales[0]));
                    jsonArray.add(Integer.MAX_VALUE);
                }
                pfopJson.add("scale", jsonArray);
            }
            pfopConfigs = new ArrayList<JsonObject>(){{
                add(pfopJson);
            }};
        }
    }

    private void setStartAndPause() throws Exception {
        String startTime = entryParam.getValue("start-time", null);
        if (startTime != null) startDateTime = checkedDatetime(startTime);
        String delay = entryParam.getValue("pause-delay", null);
        if (startTime != null) pauseDelay = Long.valueOf(ParamsUtils.checked(delay, "pause-delay", "\\d+"));
        String duration = entryParam.getValue("pause-duration", null);
        if (startTime != null) pauseDuration = Long.valueOf(ParamsUtils.checked(duration, "pause-duration", "\\d+"));
    }

    public void setEntryParam(IEntryParam entryParam) {
        this.entryParam = entryParam;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public void setRequestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setQiniuAccessKey(String qiniuAccessKey) {
        this.qiniuAccessKey = qiniuAccessKey;
    }

    public void setQiniuSecretKey(String qiniuSecretKey) {
        this.qiniuSecretKey = qiniuSecretKey;
    }

    public void setTencentSecretId(String tencentSecretId) {
        this.tencentSecretId = tencentSecretId;
    }

    public void setTencentSecretKey(String tencentSecretKey) {
        this.tencentSecretKey = tencentSecretKey;
    }

    public void setAliyunAccessId(String aliyunAccessId) {
        this.aliyunAccessId = aliyunAccessId;
    }

    public void setAliyunAccessSecret(String aliyunAccessSecret) {
        this.aliyunAccessSecret = aliyunAccessSecret;
    }

    public void setUpyunUsername(String upyunUsername) {
        this.upyunUsername = upyunUsername;
    }

    public void setUpyunPassword(String upyunPassword) {
        this.upyunPassword = upyunPassword;
    }

    public void setS3AccessId(String s3AccessId) {
        this.s3AccessId = s3AccessId;
    }

    public void setS3SecretKey(String s3SecretKey) {
        this.s3SecretKey = s3SecretKey;
    }

    public void setHuaweiAccessId(String huaweiAccessId) {
        this.huaweiAccessId = huaweiAccessId;
    }

    public void setHuaweiSecretKey(String huaweiSecretKey) {
        this.huaweiSecretKey = huaweiSecretKey;
    }

    public void setBaiduAccessId(String baiduAccessId) {
        this.baiduAccessId = baiduAccessId;
    }

    public void setBaiduSecretKey(String baiduSecretKey) {
        this.baiduSecretKey = baiduSecretKey;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public void setPrefixesMap(Map<String, Map<String, String>> prefixesMap) {
        this.prefixesMap = prefixesMap;
    }

    public void setAntiPrefixes(List<String> antiPrefixes) {
        this.antiPrefixes = antiPrefixes;
    }

    public void setPrefixLeft(boolean prefixLeft) {
        this.prefixLeft = prefixLeft;
    }

    public void setPrefixRight(boolean prefixRight) {
        this.prefixRight = prefixRight;
    }

    public void setParse(String parse) {
        this.parse = parse;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

    public void setAddKeyPrefix(String addKeyPrefix) {
        this.addKeyPrefix = addKeyPrefix;
    }

    public void setRmKeyPrefix(String rmKeyPrefix) {
        this.rmKeyPrefix = rmKeyPrefix;
    }

    public void setLinesMap(Map<String, String> linesMap) {
        this.linesMap = linesMap;
    }

    public void setProcess(String process) {
        this.process = process;
    }

    public void setPrivateType(String privateType) {
        this.privateType = privateType;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public void setBaseFilter(BaseFilter<Map<String, String>> baseFilter) {
        this.baseFilter = baseFilter;
    }

    public void setSeniorFilter(SeniorFilter<Map<String, String>> seniorFilter) {
        this.seniorFilter = seniorFilter;
    }

    public void setIndexMap(HashMap<String, String> indexMap) {
        this.indexMap = indexMap;
    }

    public void setUnitLen(int unitLen) {
        this.unitLen = unitLen;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    public void setSaveTotal(boolean saveTotal) {
        this.saveTotal = saveTotal;
    }

    public void setSavePath(String savePath) {
        this.savePath = savePath;
    }

    public void setSaveTag(String saveTag) {
        this.saveTag = saveTag;
    }

    public void setSaveFormat(String saveFormat) {
        this.saveFormat = saveFormat;
    }

    public void setRmFields(List<String> rmFields) {
        this.rmFields = rmFields;
    }

    public void setMapLine(Map<String, String> mapLine) {
        this.mapLine = mapLine;
    }

    public void setPfopConfigs(List<JsonObject> pfopConfigs) {
        this.pfopConfigs = pfopConfigs;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public void setPauseDelay(long pauseDelay) {
        this.pauseDelay = pauseDelay;
    }

    public void setPauseDuration(long pauseDuration) {
        this.pauseDuration = pauseDuration;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public String getPath() {
        return path;
    }

    public String getSource() {
        return source;
    }

    public String getQiniuAccessKey() {
        return qiniuAccessKey;
    }

    public String getQiniuSecretKey() {
        return qiniuSecretKey;
    }

    public String getTencentSecretId() {
        return tencentSecretId;
    }

    public String getTencentSecretKey() {
        return tencentSecretKey;
    }

    public String getAliyunAccessId() {
        return aliyunAccessId;
    }

    public String getAliyunAccessSecret() {
        return aliyunAccessSecret;
    }

    public String getUpyunUsername() {
        return upyunUsername;
    }

    public String getUpyunPassword() {
        return upyunPassword;
    }

    public String getS3AccessId() {
        return s3AccessId;
    }

    public String getS3SecretKey() {
        return s3SecretKey;
    }

    public String getHuaweiAccessId() {
        return huaweiAccessId;
    }

    public String getHuaweiSecretKey() {
        return huaweiSecretKey;
    }

    public String getBaiduAccessId() {
        return baiduAccessId;
    }

    public String getBaiduSecretKey() {
        return baiduSecretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public Map<String, Map<String, String>> getPrefixesMap() {
        return prefixesMap;
    }

    public List<String> getAntiPrefixes() {
        return antiPrefixes;
    }

    public boolean getPrefixLeft() {
        return prefixLeft;
    }

    public boolean getPrefixRight() {
        return prefixRight;
    }

    public String getParse() {
        return parse;
    }

    public String getSeparator() {
        return separator;
    }

    public String getAddKeyPrefix() {
        return addKeyPrefix;
    }

    public String getRmKeyPrefix() {
        return rmKeyPrefix;
    }

    public Map<String, String> getLinesMap() {
        return linesMap;
    }

    public String getProcess() {
        return process;
    }

    public String getPrivateType() {
        return privateType;
    }

    public String getRegionName() {
        return regionName;
    }

    public BaseFilter<Map<String, String>> getBaseFilter() {
        return baseFilter;
    }

    public SeniorFilter<Map<String, String>> getSeniorFilter() {
        return seniorFilter;
    }

    public Map<String, String> getIndexMap() {
        return indexMap;
    }

    public int getUnitLen() {
        return unitLen;
    }

    public int getThreads() {
        return threads;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public Boolean getSaveTotal() {
        return saveTotal;
    }

    public String getSavePath() {
        return savePath;
    }

    public String getSaveTag() {
        return saveTag;
    }

    public String getSaveFormat() {
        return saveFormat;
    }

    public String getSaveSeparator() {
        return saveSeparator;
    }

    public List<String> getRmFields() {
        return rmFields;
    }

    public Map<String, String> getMapLine() {
        return mapLine;
    }

    public List<JsonObject> getPfopConfigs() {
        return pfopConfigs;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public long getPauseDelay() {
        return pauseDelay;
    }

    public long getPauseDuration() {
        return pauseDuration;
    }
}
