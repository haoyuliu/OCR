package com.baidu.ocr.demo.model;

import com.baidu.ocr.sdk.model.IDCardParams;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author: Nemo
 * @Date: 2019-12-23
 * @Description
 */
public class IDCardParamsBean extends IDCardParams {
    private boolean detectPhoto;
    private boolean detect_photo;
    private boolean detectRectify;

    public boolean isDetectPhoto() {
        return detectPhoto;
    }

    public void setDetectPhoto(boolean detectPhoto) {
        this.detectPhoto = detectPhoto;
    }

    public boolean isDetect_photo() {
        return detect_photo;
    }

    public void setDetect_photo(boolean detect_photo) {
        this.detect_photo = detect_photo;
    }

    public boolean isDetectRectify() {
        return detectRectify;
    }

    public void setDetectRectify(boolean detectRectify) {
        this.detectRectify = detectRectify;
    }
    @Override
    public Map<String, String> getStringParams() {
        Map<String, String> stringMap = new HashMap();
        stringMap.put("id_card_side", super.getIdCardSide());
        if (super.isDetectDirection()) {
            stringMap.put("detect_direction", "true");
        } else {
            stringMap.put("detect_direction", "false");
        }

        stringMap.put("detect_risk", "false");
        stringMap.put("detect_photo", "true");

        return stringMap;
    }
}
