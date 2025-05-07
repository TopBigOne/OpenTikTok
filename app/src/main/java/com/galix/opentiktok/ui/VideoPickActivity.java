package com.galix.opentiktok.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.galix.avcore.util.FileUtils;
import com.galix.avcore.util.VideoUtil;
import com.galix.opentiktok.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;


/**
 * 资源筛选Activity
 * 固定从/sdcard开始搜索
 *
 * @Author:Galis
 * @Date:2022.01.16
 */
public class VideoPickActivity extends BaseActivity {

    private static final String                          TAG      = "VideoPickActivity : ";
    public static final  int                             REQ_PICK = 0;
    private              HandlerThread                   mLoadThread;
    private              Handler                         mLoadHandler;
    private              ArrayList<VideoUtil.FileEntry>  mFileCache;
    private              RecyclerView                    mRecyclerView;
    private              ContentLoadingProgressBar       mProgressBar;
    public static        LinkedList<VideoUtil.FileEntry> mFiles   = new LinkedList<>();

    public static void start(Activity context) {
        Intent intent = new Intent(context, VideoPickActivity.class);
        context.startActivityForResult(intent, REQ_PICK);
    }

    // ffmpeg -re -i  /Users/dev/Desktop/mp4/三十首超好听民谣.mp4  -vcodec copy -f flv rtmp://localhost:1953/mytv/room
    // ffmpeg -re -i  /Users/dev/Desktop/mp4/三十首超好听民谣.mp4  -vcodec copy -f flv rtmp://127.0.0.1:1953/live
    // rtmp://localhost:1953/live

    // git clone https://github.com/Homebrew/homebrew-core /usr/local/Homebrew/Library/Taps/homebrew/homebrew-core --depth=1
    // git clone https://github.com/Homebrew/homebrew-cask /opt/homebrew/Library/Taps/homebrew/homebrew-cask

    private static class ImageViewHolder extends RecyclerView.ViewHolder {

        public ImageView imageView;
        public ImageView pickBtn;
        public TextView  textView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFiles.clear();
        setContentView(R.layout.activity_video_pick);

        //Actionbar
        getSupportActionBar().setTitle(R.string.choose_video);
        mProgressBar = findViewById(R.id.pb_loading);
        mProgressBar.hide();
        mRecyclerView = findViewById(R.id.recyclerview_preview);
        mRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        mRecyclerView.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View            layout          = getLayoutInflater().inflate(R.layout.layout_video_info, parent, false);
                ImageViewHolder imageViewHolder = new ImageViewHolder(layout);
                imageViewHolder.itemView.getLayoutParams().width = parent.getMeasuredWidth() / 2;
                imageViewHolder.itemView.getLayoutParams().height = parent.getMeasuredWidth() / 2;
                imageViewHolder.imageView = layout.findViewById(R.id.image_video_thumb);
                imageViewHolder.pickBtn = layout.findViewById(R.id.image_pick);
                imageViewHolder.textView = layout.findViewById(R.id.text_video_info);
                return imageViewHolder;
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ImageViewHolder imageViewHolder = (ImageViewHolder) holder;
                imageViewHolder.imageView.setImageBitmap(mFileCache.get(position).thumb);
                VideoUtil.FileEntry fileEntry = mFileCache.get(position);
                imageViewHolder.pickBtn.setSelected(mFiles.contains(fileEntry));
                imageViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mFiles.contains(fileEntry)) {
                            mFiles.remove(fileEntry);
                        } else {
                            mFiles.add(fileEntry);
                        }
                        notifyDataSetChanged();
                    }
                });
                imageViewHolder.textView.setText(String.format("width:%d\nheight:%d\nduration:%ds\npath:%s", fileEntry.width, fileEntry.height, fileEntry.duration / 1000000, fileEntry.path));
            }

            @Override
            public int getItemCount() {
                return mFileCache.size();
            }

        });

        //创建线程开始加载
        mLoadThread = new HandlerThread("LoadResource");
        mLoadThread.start();
        mLoadHandler = new Handler(mLoadThread.getLooper());

        initVideoFrameData();


    }

    private void initVideoFrameData() {
        if (mLoadHandler == null) {
            return;

        }


        mLoadHandler.post(() -> {
            long now1 = System.currentTimeMillis();
            mFileCache = new ArrayList<>();
            List<String> targetPaths = new LinkedList<>();
            targetPaths.add(getCacheDir().toString());

            // 针对三星手机；
            String cameraPath= Environment.getExternalStorageDirectory().getPath()+"/DCIM/Camera";
            targetPaths.add(cameraPath);//搜索sdcard目录
            targetPaths.add(FileUtils.getCompositeDir(VideoPickActivity.this));//搜索cache composite目录

            Log.d(TAG, "initVideoFrameData: targetPaths : " + targetPaths);


            List<File> mp4List = new LinkedList<>();
            for (String path : targetPaths) {
                File dir = new File(path);
                if (!dir.exists()) {
                    continue;
                }
                File[] mp4s = dir.listFiles((dir1, name) -> name.endsWith(".mp4"));
                if (mp4s != null && mp4s.length > 0) {
                    mp4List.addAll(Arrays.asList(mp4s));
                }
            }
            //            List<File> mp4List = new LinkedList<>();
            //            mp4List.add(new File("/sdcard/test.mp4"));

            Log.d(TAG, "initVideoFrameData: mp4List : " + mp4List);




            for (File mp4 : mp4List) {
                MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                String                 mp4FilePath            = mp4.getAbsolutePath();
                try {
                    mediaMetadataRetriever.setDataSource(mp4FilePath);
                    int extractMetadataCount = 0;
                    int totalFrames = 0;
                    // 视频中 帧的数量
                    String s = null;

                    VideoUtil.FileEntry fileEntry = new VideoUtil.FileEntry();
                    // android 28
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        s = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
                        totalFrames = Integer.parseInt(s);

                    } else {
                        MediaExtractor extractor = new MediaExtractor();
                        try {
                            extractor.setDataSource(mp4FilePath);

                            for (int i = 0; i < extractor.getTrackCount(); i++) {
                                MediaFormat format = extractor.getTrackFormat(i);

                                if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                                    int  frameRate  = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                                    fileEntry.frameRate = frameRate;
                                    long durationUs = format.getLong(MediaFormat.KEY_DURATION);
                                    totalFrames += durationUs / (1000000 / frameRate);
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            extractor.release();
                        }

                    }
                    if (totalFrames <= 0) {
                        continue;
                    }


                    Log.d(TAG, mp4.getAbsolutePath());

                    fileEntry.duration = Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000L;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        fileEntry.thumb = mediaMetadataRetriever.getFrameAtIndex(0);
                    } else {
                        long durationUs = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

                        // 计算指定索引位置的时间点
                        long targetTimeUs = 0 * 1000000 / fileEntry.frameRate;

                        // 获取指定时间点的帧
                        fileEntry.thumb = mediaMetadataRetriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    }

                    fileEntry.width = Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                    fileEntry.height = Integer.parseInt(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                    fileEntry.path = mp4.getAbsolutePath();
                    fileEntry.adjustPath = VideoUtil.getAdjustGopVideoPath(VideoPickActivity.this, fileEntry.path);
                    mFileCache.add(fileEntry);
                    getWindow().getDecorView().post(() -> mRecyclerView.getAdapter().notifyDataSetChanged());


                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "initVideoFrameData ERROR : the video is " + mp4FilePath);
                    continue;
                } finally {
                    mediaMetadataRetriever.release();
                }
            }

            long now2 = System.currentTimeMillis();
            Log.d(TAG, "Filter mp4 on /sdcard : Use#" + (now2 - now1));
        });


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_video_pick, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        if (item.getItemId() == R.id.action_done) {
            handleVideo();
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mLoadThread != null) {
            try {
                mLoadHandler.getLooper().quit();
                mLoadThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleVideo() {
        //跳转前先处理资源
        if (!mProgressBar.isShown()) {
            mProgressBar.show();
        }
        VideoUtil.processVideo(VideoPickActivity.this, mFiles, msg -> {
            setResult(REQ_PICK);
            finish();
            return true;
        });
    }
}