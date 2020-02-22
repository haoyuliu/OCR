/*
 * Copyright (C) 2017 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.ocr.demo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.baidu.ocr.demo.model.IDCardParamsBean;
import com.baidu.ocr.demo.model.IDCardResultBean;
import com.baidu.ocr.demo.util.IDCardResultBeanParser;
import com.baidu.ocr.sdk.OCR;
import com.baidu.ocr.sdk.OnResultListener;
import com.baidu.ocr.sdk.exception.OCRError;
import com.baidu.ocr.sdk.model.IDCardParams;
import com.baidu.ocr.sdk.model.IDCardResult;
import com.baidu.ocr.sdk.utils.DeviceUtil;
import com.baidu.ocr.sdk.utils.HttpUtil;
import com.baidu.ocr.sdk.utils.IDCardResultParser;
import com.baidu.ocr.sdk.utils.ImageUtil;
import com.baidu.ocr.sdk.utils.Parser;
import com.baidu.ocr.ui.camera.CameraActivity;
import com.baidu.ocr.ui.camera.CameraNativeHelper;
import com.baidu.ocr.ui.camera.CameraView;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

public class IDCardActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_IMAGE_FRONT = 201;
    private static final int REQUEST_CODE_PICK_IMAGE_BACK = 202;
    private static final int REQUEST_CODE_CAMERA = 102;
    private final String TAG = "IDCardActivity";

    private TextView infoTextView;
    private AlertDialog.Builder alertDialog;
    private ImageView ivPhoto;
    private Bitmap bitmap;
    private TextView tvChangeBg;
    private boolean checkGalleryPermission() {
        int ret = ActivityCompat.checkSelfPermission(IDCardActivity.this, Manifest.permission
                .READ_EXTERNAL_STORAGE);
        if (ret != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(IDCardActivity.this,
                    new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                    1000);
            return false;
        }
        return true;
    }
    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message message) {
            if(message.obj instanceof Bitmap){
                ivPhoto.setImageBitmap((Bitmap) message.obj);
            }
            return false;
        }
    });
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_idcard);
        alertDialog = new AlertDialog.Builder(this);
        infoTextView = findViewById(R.id.info_text_view);
        ivPhoto = findViewById(R.id.iv_photo);
        tvChangeBg =findViewById(R.id.tv_change_bg);
        tvChangeBg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changePhoto();
            }
        });
        //  初始化本地质量控制模型,释放代码在onDestory中
        //  调用身份证扫描必须加上 intent.putExtra(CameraActivity.KEY_NATIVE_MANUAL, true); 关闭自动初始化和释放本地模型
        CameraNativeHelper.init(this, OCR.getInstance(this).getLicense(),
                new CameraNativeHelper.CameraNativeInitCallback() {
            @Override
            public void onError(int errorCode, Throwable e) {
                String msg;
                switch (errorCode) {
                    case CameraView.NATIVE_SOLOAD_FAIL:
                        msg = "加载so失败，请确保apk中存在ui部分的so";
                        break;
                    case CameraView.NATIVE_AUTH_FAIL:
                        msg = "授权本地质量控制token获取失败";
                        break;
                    case CameraView.NATIVE_INIT_FAIL:
                        msg = "本地质量控制";
                        break;
                    default:
                        msg = String.valueOf(errorCode);
                }
                infoTextView.setText("本地质量控制初始化错误，错误原因： " + msg);
            }
        });
        
        findViewById(R.id.gallery_button_front).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkGalleryPermission()) {
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    intent.setType("image/*");
                    startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE_FRONT);
                }
            }
        });

        findViewById(R.id.gallery_button_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkGalleryPermission()) {
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    intent.setType("image/*");
                    startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE_BACK);
                }
            }
        });

        // 身份证正面拍照
        findViewById(R.id.id_card_front_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(IDCardActivity.this, CameraActivity.class);
                intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH,
                        FileUtil.getSaveFile(getApplication()).getAbsolutePath());
                intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, CameraActivity.CONTENT_TYPE_ID_CARD_FRONT);
                startActivityForResult(intent, REQUEST_CODE_CAMERA);
            }
        });

        // 身份证正面扫描
        findViewById(R.id.id_card_front_button_native).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(IDCardActivity.this, CameraActivity.class);
                intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH,
                        FileUtil.getSaveFile(getApplication()).getAbsolutePath());
                intent.putExtra(CameraActivity.KEY_NATIVE_ENABLE,
                        true);
                // KEY_NATIVE_MANUAL设置了之后CameraActivity中不再自动初始化和释放模型
                // 请手动使用CameraNativeHelper初始化和释放模型
                // 推荐这样做，可以避免一些activity切换导致的不必要的异常
                intent.putExtra(CameraActivity.KEY_NATIVE_MANUAL,
                        true);
                intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, CameraActivity.CONTENT_TYPE_ID_CARD_FRONT);
                startActivityForResult(intent, REQUEST_CODE_CAMERA);
            }
        });

        // 身份证反面拍照
        findViewById(R.id.id_card_back_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(IDCardActivity.this, CameraActivity.class);
                intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH,
                        FileUtil.getSaveFile(getApplication()).getAbsolutePath());
                intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, CameraActivity.CONTENT_TYPE_ID_CARD_BACK);
                startActivityForResult(intent, REQUEST_CODE_CAMERA);
            }
        });

        // 身份证反面扫描
        findViewById(R.id.id_card_back_button_native).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(IDCardActivity.this, CameraActivity.class);
                intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH,
                        FileUtil.getSaveFile(getApplication()).getAbsolutePath());
                intent.putExtra(CameraActivity.KEY_NATIVE_ENABLE,
                        true);
                // KEY_NATIVE_MANUAL设置了之后CameraActivity中不再自动初始化和释放模型
                // 请手动使用CameraNativeHelper初始化和释放模型
                // 推荐这样做，可以避免一些activity切换导致的不必要的异常
                intent.putExtra(CameraActivity.KEY_NATIVE_MANUAL,
                        true);
                intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, CameraActivity.CONTENT_TYPE_ID_CARD_BACK);
                startActivityForResult(intent, REQUEST_CODE_CAMERA);
            }
        });
    }

    private void recIDCard(String idCardSide, String filePath) {
        IDCardParamsBean param = new IDCardParamsBean();
        param.setImageFile(new File(filePath));
        // 设置身份证正反面
        param.setIdCardSide(idCardSide);
        // 设置方向检测
        param.setDetectDirection(true);
        // 设置图像参数压缩质量0-100, 越大图像质量越好但是请求时间越长。 不设置则默认值为20
        param.setImageQuality(20);
        param.setDetectPhoto(true);
        param.setDetect_photo(true);
        recognizeIDCard(param, new OnResultListener<IDCardResultBean>() {
            @Override
            public void onResult(IDCardResultBean result) {
                if (result != null) {
                    byte[] decodedString = Base64.decode(result.getPhoto(), Base64.DEFAULT);
                    bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    ivPhoto.setImageBitmap(bitmap);
                    alertText("", result.toString());
                }
            }

            @Override
            public void onError(OCRError error) {
                alertText("", error.getMessage());
            }
        });
    }
    public void recognizeIDCard(final IDCardParams param, final OnResultListener<IDCardResultBean> listener) {
        File imageFile = param.getImageFile();
        final File tempImage = new File(getApplication().getCacheDir(), String.valueOf(System.currentTimeMillis()));
        ImageUtil.resize(imageFile.getAbsolutePath(), tempImage.getAbsolutePath(), 1280, 1280, param.getImageQuality());
        param.setImageFile(tempImage);
        final Parser<IDCardResultBean> idCardResultParser = new IDCardResultBeanParser(param.getIdCardSide());
        HttpUtil.getInstance().post(urlAppendCommonParams("https://aip.baidubce.com/rest/2.0/ocr/v1/idcard?"), param, idCardResultParser, new OnResultListener<IDCardResultBean>() {
            @Override
            public void onResult(IDCardResultBean result) {
                tempImage.delete();
                if (listener != null) {
                    listener.onResult(result);
                }

            }

            @Override
            public void onError(OCRError error) {
                tempImage.delete();
                if (listener != null) {
                    listener.onError(error);
                }

            }
        });
    }
    private String urlAppendCommonParams(String url) {
        StringBuilder sb = new StringBuilder(url);
        sb.append("access_token=").append(OCR.getInstance(getApplication()).getAccessToken().getAccessToken());
        sb.append("&aipSdk=Android");
        sb.append("&aipSdkVersion=").append("1_4_4");
        sb.append("&aipDevid=").append(DeviceUtil.getDeviceId(getApplication()));
        return sb.toString();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_IMAGE_FRONT && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            String filePath = getRealPathFromURI(uri);
            recIDCard(IDCardParams.ID_CARD_SIDE_FRONT, filePath);
        }

        if (requestCode == REQUEST_CODE_PICK_IMAGE_BACK && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            String filePath = getRealPathFromURI(uri);
            recIDCard(IDCardParams.ID_CARD_SIDE_BACK, filePath);
        }

        if (requestCode == REQUEST_CODE_CAMERA && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                String contentType = data.getStringExtra(CameraActivity.KEY_CONTENT_TYPE);
                String filePath = FileUtil.getSaveFile(getApplicationContext()).getAbsolutePath();
                if (!TextUtils.isEmpty(contentType)) {
                    if (CameraActivity.CONTENT_TYPE_ID_CARD_FRONT.equals(contentType)) {
                        recIDCard(IDCardParams.ID_CARD_SIDE_FRONT, filePath);
                    } else if (CameraActivity.CONTENT_TYPE_ID_CARD_BACK.equals(contentType)) {
                        recIDCard(IDCardParams.ID_CARD_SIDE_BACK, filePath);
                    }
                }
            }
        }
    }

    private void alertText(final String title, final String message) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                alertDialog.setTitle(title)
                        .setMessage(message)
                        .setPositiveButton("确定", null)
                        .show();
            }
        });
    }

    private String getRealPathFromURI(Uri contentURI) {
        String result;
        Cursor cursor = getContentResolver().query(contentURI, null, null, null, null);
        if (cursor == null) { // Source is Dropbox or other similar local file path
            result = contentURI.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    @Override
    protected void onDestroy() {
        // 释放本地质量控制模型
        CameraNativeHelper.release();
        super.onDestroy();
    }

    /**
     * 改变图片背景色
     */
    private void changePhoto() {
        if (bitmap==null){
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG,"start handle photo");
                startDetail(bitmap);
            }
        }).start();
    }
    private void startDetail(Bitmap bitmap) {
        Mat image = new Mat();
        Utils.bitmapToMat(bitmap, image);

        Mat hsvImg = new Mat();
        Imgproc.cvtColor(image, hsvImg, Imgproc.COLOR_BGR2HSV);
        List<Mat> list = new ArrayList<>();
        Core.split(hsvImg, list);

        Mat roiH = list.get(0).submat(new Rect(0, 0, 20, 20));
        Mat roiS = list.get(1).submat(new Rect(0, 0, 20, 20));

        Log.i(TAG,"start sum bg");
        int SumH = 0;
        int SumS = 0;
        byte[] h = new byte[1];
        byte[] s = new byte[1];
        //取一块蓝色背景，计算出它的平均色调和平均饱和度
        for (int i = 0; i < 20; i++) {
            for (int j = 0; j < 20; j++) {
                roiH.get(j, i, h);
                roiS.get(j, i, s);

                SumH = h[0] + SumH;
                SumS = s[0] + SumS;
            }
        }
        int avgH, avgS;//蓝底的平均色调和平均饱和度
        avgH = SumH / 400;
        avgS = SumS / 400;

        Log.i(TAG,"depth="+list.get(0).depth());
        Log.i(TAG,"start sum detail all photo");
        //遍历整个图像
        int nl = hsvImg.height();
        int nc = hsvImg.width();
//        byte[] changeColor = new byte[]{127};

        byte[] hArray = new byte[nl * nc];
        byte[] sArray = new byte[nl * nc];
        byte[] vArray = new byte[nl * nc];

        list.get(0).get(0,0,hArray);
        list.get(1).get(0,0,sArray);
        list.get(2).get(0,0,vArray);

        int row,index;
        for (int j = 0; j < nl; j++) {
            row = j * nc;
            for (int i = 0; i < nc; i++) {
                index = row + i;

                if(hArray[index] <= (avgH + 20) && hArray[index] >= (avgH - 20)
                        && sArray[index] <= (avgS + 400)
                        && sArray[index] >= (avgS -400)
                ){
                    hArray[index] = 0;
                    sArray[index] = 0;
                }
            }
        }

        list.get(0).put(0,0,hArray);
        list.get(1).put(0,0,sArray);


        Log.i(TAG,"merge photo");
        Core.merge(list,hsvImg);

        Imgproc.cvtColor(hsvImg,image, Imgproc.COLOR_HSV2BGR);

        Bitmap resultBitmap = Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(), Bitmap.Config.RGB_565);
        Utils.matToBitmap(image,resultBitmap);
        Message obtain = Message.obtain();
        obtain.obj = resultBitmap;
        handler.sendMessage(obtain);
    }
}
