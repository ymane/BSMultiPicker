package com.yogesh.multipickerdemo;

import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;


import com.bumptech.glide.Glide;
import com.yogesh.bsmultipicker.BSMultiPicker;

import java.util.List;

public class MainActivity extends AppCompatActivity implements BSMultiPicker.OnSingleImageSelectedListener,
        BSMultiPicker.OnMultiImageSelectedListener,BSMultiPicker.OnVideoSelectedListener {

    private ImageView ivImage1, ivImage2, ivImage3, ivImage4, ivImage5, ivImage6,iv_video;
    private LinearLayout image_layout;
    private FrameLayout video_layout;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ivImage1 = findViewById(R.id.iv_image1);
        ivImage2 = findViewById(R.id.iv_image2);
        ivImage3 = findViewById(R.id.iv_image3);
        ivImage4 = findViewById(R.id.iv_image4);
        ivImage5 = findViewById(R.id.iv_image5);
        ivImage6 = findViewById(R.id.iv_image6);
        iv_video = findViewById(R.id.iv_video);
        image_layout = findViewById(R.id.image_layout);
        video_layout = findViewById(R.id.video_layout);
        video_layout.setVisibility(View.GONE);
        findViewById(R.id.tv_single_selection).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BSMultiPicker pickerDialog = new BSMultiPicker.Builder("com.yogesh.multipickerdemo.fileprovider")
                        .build();
                pickerDialog.show(getSupportFragmentManager(), "picker");
            }
        });
        findViewById(R.id.tv_multi_selection).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BSMultiPicker pickerDialog = new BSMultiPicker.Builder("com.yogesh.multipickerdemo.fileprovider")
                        .setMaximumDisplayingImages(Integer.MAX_VALUE)
                        .isMultiSelect()
                        .setMinimumMultiSelectCount(2)
                        .setMaximumMultiSelectCount(10)
                        .build();
                pickerDialog.show(getSupportFragmentManager(), "picker");
            }
        });
        findViewById(R.id.tv_multi_selection_with_video).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BSMultiPicker pickerDialog = new BSMultiPicker.Builder("com.yogesh.multipickerdemo.fileprovider")
                        .setMaximumDisplayingImages(Integer.MAX_VALUE)
                        .showVideo(true)
                        .isMultiSelect()
                        .setMinimumMultiSelectCount(1)
                        .setMaximumMultiSelectCount(10)
                        .build();
                pickerDialog.show(getSupportFragmentManager(), "picker");
            }
        });
    }

    @Override
    public void onSingleImageSelected(Uri uri) {
        video_layout.setVisibility(View.GONE);
        image_layout.setVisibility(View.VISIBLE);
        Glide.with(MainActivity.this).load(uri).into(ivImage2);
    }

    @Override
    public void onMultiImageSelected(List<Uri> uriList) {
        video_layout.setVisibility(View.GONE);
        image_layout.setVisibility(View.VISIBLE);
        for (int i=0; i < uriList.size(); i++) {
            if (i >= 6) return;
            ImageView iv;
            switch (i) {
                case 0:
                    iv = ivImage1;
                    break;
                case 1:
                    iv = ivImage2;
                    break;
                case 2:
                    iv = ivImage3;
                    break;
                case 3:
                    iv = ivImage4;
                    break;
                case 4:
                    iv = ivImage5;
                    break;
                case 5:
                default:
                    iv = ivImage6;
            }
            Glide.with(this).load(uriList.get(i)).into(iv);
        }
    }

    @Override
    public void onVideoSelected(Uri uri) {
        video_layout.setVisibility(View.VISIBLE);
        image_layout.setVisibility(View.GONE);
        Glide.with(this)
                .load( uri )
                .into(iv_video);
       /// ivImage2.setImageBitmap(ThumbnailUtils.createVideoThumbnail(uri.getPath(), MediaStore.Video.Thumbnails.MINI_KIND));
    }
}
