package com.qiniu.datasource;

import com.obs.services.ObsClient;
import com.obs.services.ObsConfiguration;
import com.obs.services.model.ObsObject;
import com.qiniu.common.SuitsException;
import com.qiniu.convert.Converter;
import com.qiniu.convert.JsonObjectPair;
import com.qiniu.convert.StringBuilderPair;
import com.qiniu.convert.StringMapPair;
import com.qiniu.interfaces.IStorageLister;
import com.qiniu.interfaces.IResultOutput;
import com.qiniu.interfaces.IStringFormat;
import com.qiniu.interfaces.ITypeConvert;
import com.qiniu.persistence.FileSaveMapper;
import com.qiniu.util.CloudApiUtils;
import com.qiniu.util.ConvertingUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class HuaweiObsContainer extends CloudStorageContainer<ObsObject, Map<String, String>> {

    private String accessKeyId;
    private String accessKeySecret;
    private ObsConfiguration configuration;

    public HuaweiObsContainer(String accessKeyId, String accessKeySecret, ObsConfiguration configuration, String endPoint,
                              String bucket, Map<String, Map<String, String>> prefixesMap, List<String> antiPrefixes,
                              boolean prefixLeft, boolean prefixRight, Map<String, String> indexMap, List<String> fields,
                              int unitLen, int threads) throws IOException {
        super(bucket, prefixesMap, antiPrefixes, prefixLeft, prefixRight, indexMap, fields, unitLen, threads);
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.configuration = configuration;
        this.configuration.setEndPoint(endPoint);
        HuaweiLister huaweiLister = new HuaweiLister(new ObsClient(accessKeyId, accessKeySecret, this.configuration), bucket,
                null, null, null, 1);
        huaweiLister.close();
        huaweiLister = null;
        ObsObject test = new ObsObject();
        test.setObjectKey("test");
        ConvertingUtils.toPair(test, indexMap, new StringMapPair());
    }

    @Override
    public String getSourceName() {
        return "huawei";
    }

    @Override
    protected ITypeConvert<ObsObject, Map<String, String>> getNewConverter() {
        return new Converter<ObsObject, Map<String, String>>() {
            @Override
            public Map<String, String> convertToV(ObsObject line) throws IOException {
                return ConvertingUtils.toPair(line, indexMap, new StringMapPair());
            }
        };
    }

    @Override
    protected ITypeConvert<ObsObject, String> getNewStringConverter() {
        IStringFormat<ObsObject> stringFormatter;
        if ("json".equals(saveFormat)) {
            stringFormatter = line -> ConvertingUtils.toPair(line, fields, new JsonObjectPair()).toString();
        } else if ("yaml".equals(saveFormat)) {
            stringFormatter = line -> ConvertingUtils.toStringWithIndent(line, fields);
        } else {
            stringFormatter = line -> ConvertingUtils.toPair(line, fields, new StringBuilderPair(saveSeparator));
        }
        return new Converter<ObsObject, String>() {
            @Override
            public String convertToV(ObsObject line) throws IOException {
                return stringFormatter.toFormatString(line);
            }
        };
    }

    @Override
    protected IResultOutput getNewResultSaver(String order) throws IOException {
        return order != null ? new FileSaveMapper(savePath, getSourceName(), order) : new FileSaveMapper(savePath);
    }

    @Override
    protected IStorageLister<ObsObject> getLister(String prefix, String marker, String start, String end, int unitLen) throws SuitsException {
        if (marker == null || "".equals(marker)) marker = CloudApiUtils.getAliOssMarker(start);
        return new HuaweiLister(new ObsClient(accessKeyId, accessKeySecret, configuration), bucket, prefix, marker, end, unitLen);
    }
}
