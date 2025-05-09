package com.galix.opentiktok.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.galix.avcore.avcore.AVAudio;
import com.galix.avcore.avcore.AVComponent;
import com.galix.avcore.avcore.AVEngine;
import com.galix.avcore.avcore.AVPag;
import com.galix.avcore.avcore.AVSticker;
import com.galix.avcore.avcore.AVVideo;
import com.galix.avcore.avcore.AVWord;
import com.galix.avcore.render.ImageViewRender;
import com.galix.avcore.render.PagRender;
import com.galix.avcore.render.TextRender;
import com.galix.avcore.util.GestureUtils;
import com.galix.avcore.util.LogUtil;
import com.galix.avcore.util.MathUtils;
import com.galix.avcore.util.VideoUtil;
import com.galix.opentiktok.R;

import org.libpag.PAGView;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import static androidx.recyclerview.widget.RecyclerView.HORIZONTAL;
import static com.galix.avcore.avcore.AVEngine.VideoState.VideoStatus.START;

/**
 * 视频编辑界面
 *
 * @Author Galis
 * @Date 2022.01.15
 */
public class VideoEditActivity extends BaseActivity {

    private static final String TAG = VideoEditActivity.class.getSimpleName();
    private LinkedList<Integer> mStickerList;//贴纸
    private SurfaceView mSurfaceView;
    private RecyclerView mTabRecyclerView;
    private VideoPreviewPanel mVideoPreViewPanel;
    private RecyclerView mStickerRecyclerView;
    private TextView mTimeInfo;
    private ImageView mPlayBtn;
    private AVEngine mAVEngine;
    private ImageView mFullScreenBtn;

    //底部ICON info
    private static final int[] TAB_INFO_LIST = {
            R.drawable.icon_cut, R.string.tab_cut,
            R.drawable.icon_audio, R.string.tab_audio,
            R.drawable.icon_word, R.string.tab_text,
            R.drawable.icon_sticker, R.string.tab_sticker,
            R.drawable.icon_pip, R.string.tab_pip,
            R.drawable.icon_effect, R.string.tab_effect,
            R.drawable.icon_filter, R.string.tab_filter,
            R.drawable.icon_ratio, R.string.tab_ratio,
            R.drawable.icon_background, R.string.tab_background,
            R.drawable.icon_adjust, R.string.tab_adjust
    };

    public static void start(Context ctx) {
        Intent intent = new Intent(ctx, VideoEditActivity.class);
        ctx.startActivity(intent);
    }


    private class ImageViewHolder extends RecyclerView.ViewHolder {
        public ImageView imageView;
        public TextView textView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    private class ThumbViewHolder extends RecyclerView.ViewHolder {
        public View view1;
        public View view2;

        public ThumbViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }


    //UI回调
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_edit);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        mSurfaceView = findViewById(R.id.glsurface_preview);
        mTimeInfo = findViewById(R.id.text_duration);
        mPlayBtn = findViewById(R.id.image_play);
        mPlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAVEngine.togglePlayPause();
                freshUI();
            }
        });
        mFullScreenBtn = findViewById(R.id.image_fullscreen);
        mTabRecyclerView = findViewById(R.id.recyclerview_tab_mode);
        mTabRecyclerView.setLayoutManager(new LinearLayoutManager(this, HORIZONTAL, false));
        mTabRecyclerView.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View layout = getLayoutInflater().inflate(R.layout.layout_tab_item, parent, false);
                ImageViewHolder imageViewHolder = new ImageViewHolder(layout);
                imageViewHolder.itemView.getLayoutParams().width = (int) (60 * getResources().getDisplayMetrics().density);
                imageViewHolder.itemView.getLayoutParams().height = (int) (60 * getResources().getDisplayMetrics().density);
                imageViewHolder.imageView = layout.findViewById(R.id.image_video_thumb);
                imageViewHolder.textView = layout.findViewById(R.id.text_video_info);
                return imageViewHolder;
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ImageViewHolder imageViewHolder = (ImageViewHolder) holder;
                imageViewHolder.imageView.setImageResource(TAB_INFO_LIST[2 * position]);
                imageViewHolder.imageView.setPadding(8, 8, 8, 8);
                imageViewHolder.textView.setText(TAB_INFO_LIST[2 * position + 1]);
                imageViewHolder.itemView.setOnClickListener(v -> {
                    int index = TAB_INFO_LIST[2 * position + 1];
                    if (index == R.string.tab_sticker) {
                        mStickerRecyclerView.setVisibility((mStickerRecyclerView.getVisibility() == View.VISIBLE) ? View.GONE : View.VISIBLE);
                    } else if (index == R.string.tab_text) {
                        EditText child = ViewUtils.createEditText(VideoEditActivity.this);
                        AVWord word = new AVWord(mAVEngine.getMainClock(), child, new TextRender(child));
                        mAVEngine.addComponent(word, args1 -> mVideoPreViewPanel.post(new Runnable() {
                            @Override
                            public void run() {
                                mVideoPreViewPanel.updateEffect();
                                bindView(child, word);
                            }
                        }));
                    } else if (index == R.string.tab_ratio) {
                        ViewGroup view = findViewById(R.id.view_ratio_tablist);
                        ((RatioTabListView) view.getChildAt(0)).buildView(1920, 1080, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                RatioTabListView.RatioInfo info = (RatioTabListView.RatioInfo) v.getTag();
                                if (info == null) {
                                    view.setVisibility(View.GONE);
                                    return;
                                }
                                mAVEngine.setCanvasType(info.text, new AVEngine.EngineCallback() {
                                    @Override
                                    public void onCallback(Object... args1) {
                                        Size size = (Size) args1[0];
                                        findViewById(R.id.rl_view_preview).getLayoutParams().width = size.getWidth();
                                        findViewById(R.id.rl_view_preview).getLayoutParams().height = size.getHeight();
                                        findViewById(R.id.rl_view_preview).requestLayout();
                                    }
                                });
                                Toast.makeText(getBaseContext(), info.text, Toast.LENGTH_SHORT).show();
                            }
                        });
                        view.setVisibility(View.VISIBLE);
                    } else if (index == R.string.tab_background) {
                        ViewGroup view = findViewById(R.id.view_background_tablist);
                        ((BackgroundTabListView) view.getChildAt(0)).buildView(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (v.getTag() == null) {
                                    view.setVisibility(View.GONE);
                                    return;
                                }
                                mAVEngine.setBgColor((Integer) v.getTag());
                            }
                        });
                        view.setVisibility(View.VISIBLE);
                    } else if (index == R.string.tab_effect) {
                        PAGView pagView = ViewUtils.createPagView(VideoEditActivity.this);
//                        pagView.setPath("assets://pag/screen_effect.pag");
                        AVPag avPag = new AVPag("pag/time_test.pag", new PagRender(pagView));
                        mAVEngine.addComponent(avPag, new AVEngine.EngineCallback() {
                            @Override
                            public void onCallback(Object... args1) {
                                mVideoPreViewPanel.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        mVideoPreViewPanel.updateEffect();
                                        pagView.getLayoutParams().height = Math.min(mSurfaceView.getMeasuredHeight(), avPag.getSize().getHeight());
                                        pagView.getLayoutParams().width = (int) (pagView.getLayoutParams().height * avPag.getSize().getWidth() / avPag.getSize().getHeight() * 1.0f);
                                        pagView.requestLayout();
                                        bindView(pagView, avPag);
                                    }
                                });
                            }
                        });
                    } else {
                        mStickerRecyclerView.setVisibility(View.GONE);
                        Toast.makeText(VideoEditActivity.this, "待实现", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public int getItemCount() {
                return TAB_INFO_LIST.length / 2;
            }
        });
        mTabRecyclerView.getAdapter().notifyDataSetChanged();

        mStickerList = new LinkedList<>();
        mStickerList.add(R.raw.aini);
        mStickerList.add(R.raw.buyuebuyue);
        mStickerList.add(R.raw.burangwo);
        mStickerList.add(R.raw.dengliao);
        mStickerList.add(R.raw.gandepiaoliang);
        mStickerList.add(R.raw.nizabushagntian);
        mStickerRecyclerView = findViewById(R.id.recyclerview_sticker);
        mStickerRecyclerView.setLayoutManager(new GridLayoutManager(this, 4));
        mStickerRecyclerView.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ImageView view = new ImageView(parent.getContext());
                view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                view.setLayoutParams(new RecyclerView.LayoutParams(parent.getMeasuredWidth() / 4,
                        parent.getMeasuredWidth() / 4));
                return new ThumbViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, @SuppressLint("RecyclerView") int position) {
                Glide.with(VideoEditActivity.this)
                        .load(mStickerList.get(position))
                        .asGif()
                        .into((ImageView) holder.itemView);
                
                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ImageView child = ViewUtils.createImageView(VideoEditActivity.this);
                        AVSticker sticker = new AVSticker(mAVEngine.getMainClock(), getResources().openRawResource(mStickerList.get(position)),
                                new ImageViewRender(child));
                        mAVEngine.addComponent(sticker, args1 -> mVideoPreViewPanel.post(new Runnable() {
                            @Override
                            public void run() {
                                mVideoPreViewPanel.updateEffect();
                                child.getLayoutParams().width = sticker.getSize().getWidth();
                                child.getLayoutParams().height = sticker.getSize().getHeight();
                                bindView(child, sticker);
                            }
                        }));
                        mStickerRecyclerView.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public int getItemCount() {
                return mStickerList.size();
            }
        });

        //初始化Thumb信息
        mAVEngine = AVEngine.getVideoEngine();
        mAVEngine.configure(mSurfaceView);
        mAVEngine.create();
        LogUtil.setLogLevel(LogUtil.LogLevel.FULL);

        mVideoPreViewPanel = findViewById(R.id.rl_video_preview_panel);
        mAVEngine.setOnFrameUpdateCallback(new AVEngine.EngineCallback() {
            @Override
            public void onCallback(Object[] args1) {
                freshUI();
            }
        });
        mVideoPreViewPanel.setClipCallback(new ClipView.ClipCallback() {
            @Override
            public void onClip(Rect src, Rect dst) {
                Map<String, Object> map = new HashMap<>();
                map.put("comm_src", src);
                map.put("comm_dst", dst);
                mAVEngine.changeComponent(mAVEngine.getVideoState().editComponent, map, new AVEngine.EngineCallback() {
                    @Override
                    public void onCallback(Object[] args1) {
                        mAVEngine.fastSeek(mAVEngine.getMainClock());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mVideoPreViewPanel.updateData(mAVEngine.getVideoState());
                                mVideoPreViewPanel.updateScroll(false);
                            }
                        });
                    }
                });
            }
        });
        mVideoPreViewPanel.setBtnAddCallback(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VideoPickActivity.start(VideoEditActivity.this);
            }
        });

        VideoPickActivity.start(this);
    }

    private void bindView(View view, AVComponent component) {
        ViewGroup parent = findViewById(R.id.rl_view_preview);
        parent.addView(view);
        view.setTag(component);
        view.requestLayout();
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                updateMatrix(view);
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
        if(view instanceof EditText){
            ((EditText)view).addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    view.requestLayout();
                    view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            updateMatrix(view);
                            view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                    });
                }
            });
        }
        //抬起手时候就设置matrix
        GestureUtils.setupView(view, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        updateMatrix(v);
                        v.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
            }
        });
    }


    private void updateMatrix(View view) {
        AVComponent avComponent = (AVComponent) view.getTag();
        avComponent.lock();
        avComponent.setMatrix(
                MathUtils.calMatrix(
                        new Rect(0, 0, mSurfaceView.getMeasuredWidth(), mSurfaceView.getMeasuredHeight()),
                        new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom())

                )
        );
        avComponent.unlock();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAVEngine.release();
        Log.d(TAG, "onDestroy");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_video_edit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case R.id.action_export:
                VideoExportActivity.start(this, VideoExportActivity.class);
                break;
            case R.id.action_pixel:
                mAVEngine.getVideoState().lock();
                if (item.getTitle().toString().equalsIgnoreCase("1080p")) {
                    item.setTitle("720P");
                    mAVEngine.getVideoState().compositeHeight = 720;
                } else {
                    item.setTitle("1080P");
                    mAVEngine.getVideoState().compositeHeight = 1080;
                }
                mAVEngine.getVideoState().unlock();
                break;
            case R.id.action_debug:
                if (findViewById(R.id.tv_debug_info).getVisibility() == View.VISIBLE) {
                    findViewById(R.id.tv_debug_info).setVisibility(View.GONE);
                } else {
                    findViewById(R.id.tv_debug_info).setVisibility(View.VISIBLE);
                    ((TextView) findViewById(R.id.tv_debug_info)).setText(AVEngine.getVideoEngine().getVideoState().toString());
                }
                ((TextView) findViewById(R.id.tv_debug_info)).setMovementMethod(ScrollingMovementMethod.getInstance());
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == VideoPickActivity.REQ_PICK) {
            LinkedList<VideoUtil.FileEntry> files = VideoPickActivity.mFiles;
            for (VideoUtil.FileEntry fileEntry : files) {
                AVVideo video = new AVVideo(true, -1, fileEntry.path, null);
                AVAudio audio = new AVAudio(-1, fileEntry.path, null);
                mAVEngine.addComponent(video, new AVEngine.EngineCallback() {
                    @Override
                    public void onCallback(Object[] args1) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mVideoPreViewPanel.updateData(mAVEngine.getVideoState());
                            }
                        });
                        mAVEngine.setCanvasType("原始", new AVEngine.EngineCallback() {
                            @Override
                            public void onCallback(Object... args1) {
                                findViewById(R.id.rl_view_preview).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Size size = (Size) args1[0];
                                        findViewById(R.id.rl_view_preview).getLayoutParams().width = size.getWidth();
                                        findViewById(R.id.rl_view_preview).getLayoutParams().height = size.getHeight();
                                        findViewById(R.id.rl_view_preview).requestLayout();
                                    }
                                });
                            }
                        });
                    }
                });
                mAVEngine.addComponent(audio, new AVEngine.EngineCallback() {
                    @Override
                    public void onCallback(Object[] args1) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mVideoPreViewPanel.updateData(mAVEngine.getVideoState());
                            }
                        });
                    }
                });
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void freshUI() {
        Log.d(TAG, "freshUI: ");
        getWindow().getDecorView().post(() -> {
            AVEngine.VideoState mVideoState = AVEngine.getVideoEngine().getVideoState();
            if (mVideoState != null) {
                long positionInMS = (AVEngine.getVideoEngine().getMainClock() + 999) / 1000;
                long durationInMS = (mVideoState.videoDuration + 999) / 1000;
                String timeInfo = String.format("%02d:%02d:%03d / %02d:%02d:%03d",
                        positionInMS / 1000 / 60 % 60, positionInMS / 1000 % 60, positionInMS % 1000,
                        durationInMS / 1000 / 60 % 60, durationInMS / 1000 % 60, durationInMS % 1000);
                
                mTimeInfo.setText(timeInfo);
                mPlayBtn.setImageResource(mVideoState.status == START ? R.drawable.icon_video_pause : R.drawable.icon_video_play);
                mVideoPreViewPanel.updateScroll(true);
            }
        });
    }


}