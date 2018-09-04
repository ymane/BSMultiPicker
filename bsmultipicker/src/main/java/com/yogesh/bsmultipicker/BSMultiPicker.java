package com.yogesh.bsmultipicker;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Px;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.BottomSheetDialog;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.FileProvider;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.yogesh.bsmultipicker.adapter.ImageTileAdapter;
import com.yogesh.bsmultipicker.utils.Utils;
import com.yogesh.bsmultipicker.widget.GridItemSpacingDecoration;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static android.app.Activity.RESULT_OK;

/**
 * This is the core class of this library, which extends BottomSheetDialogFragment
 * from the design support library, in order to provide the basic architecture of a bottom sheet.
 * <p>
 * It is also responsible for:
 * - Handling permission
 * - Communicate with caller activity / fragment
 * - As a view controller
 */

public class BSMultiPicker extends BottomSheetDialogFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private String TAG="BSMultiPicker";
    private static final int LOADER_ID = 1000;
    private static final int VIDEO_LOADER_ID = 1001;

    private static final int PERMISSION_READ_STORAGE = 2001;
    private static final int PERMISSION_CAMERA = 2002;
    private static final int PERMISSION_WRITE_STORAGE = 2003;

    private static final int REQUEST_TAKE_PHOTO = 3001;
    private static final int REQUEST_SELECT_FROM_GALLERY = 3002;

    //Views
    private RecyclerView recyclerView;
    private View bottomBarView;
    private TextView tvDone, tvMultiSelectMessage;

    private BottomSheetBehavior bottomSheetBehavior;

    //Components
    private ImageTileAdapter adapter;

    //Callbacks
    public interface OnVideoSelectedListener {
        void onVideoSelected(Uri uri);
    }
    private OnVideoSelectedListener onVideoSelectedListener;
    //Callbacks
    public interface OnSingleImageSelectedListener {
        void onSingleImageSelected(Uri uri);
    }
    private OnSingleImageSelectedListener onSingleImageSelectedListener;
    public interface OnMultiImageSelectedListener {
        void onMultiImageSelected(List<Uri> uriList);
    }
    private OnMultiImageSelectedListener onMultiImageSelectedListener;

    //States
    private boolean isMultiSelection = false;
    private Uri currentPhotoUri;

    //Configurations
    private int maximumDisplayingImages = Integer.MAX_VALUE;
    private int peekHeight = Utils.dp2px(360);
    private int minimumMultiSelectCount = 1;
    private int maximumMultiSelectCount = Integer.MAX_VALUE;
    private String providerAuthority;
    private boolean showCameraTile = true;
    private boolean showGalleryTile = true;
    private boolean showVideo = false;
    private int spanCount = 4;
    private int gridSpacing = Utils.dp2px(2);
    private int multiSelectBarBgColor = android.R.color.white;
    private int multiSelectTextColor = R.color.primary_text;
    private int multiSelectDoneTextColor = R.color.multiselect_done;
    private boolean showOverSelectMessage = true;
    private int overSelectTextColor = R.color.error_text;

    /**
     * Here we check if the caller Activity has registered callback and reference it.
     */
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
          if (context instanceof OnSingleImageSelectedListener) {
            onSingleImageSelectedListener = (OnSingleImageSelectedListener) context;
        }
        if (context instanceof OnMultiImageSelectedListener) {
            onMultiImageSelectedListener = (OnMultiImageSelectedListener) context;
        }

        if (context instanceof OnVideoSelectedListener) {
            onVideoSelectedListener = (OnVideoSelectedListener) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadConfigFromBuilder();
        if (Utils.isReadStorageGranted(getContext())) {
            getLoaderManager().initLoader(LOADER_ID, null, BSMultiPicker.this);
          //  getLoaderManager().initLoader(VIDEO_LOADER_ID, null, BSMultiPicker.this);
        } else {
            Utils.checkPermission(BSMultiPicker.this, Manifest.permission.READ_EXTERNAL_STORAGE, PERMISSION_READ_STORAGE);
        }
        if (savedInstanceState != null) {
            currentPhotoUri = savedInstanceState.getParcelable("currentPhotoUri");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_imagepicker_sheet, container, false);
        bindViews(view);
        setupRecyclerView();
         /*
         Here we check if the parent fragment has registered callback and reference it.
         */
        if (getParentFragment() != null && getParentFragment() instanceof OnSingleImageSelectedListener) {
            onSingleImageSelectedListener = (OnSingleImageSelectedListener) getParentFragment();
        }
        if (getParentFragment() != null && getParentFragment() instanceof OnMultiImageSelectedListener) {
            onMultiImageSelectedListener = (OnMultiImageSelectedListener) getParentFragment();
        }

        if (getParentFragment() != null && getParentFragment() instanceof OnVideoSelectedListener) {
            onVideoSelectedListener = (OnVideoSelectedListener) getParentFragment();
        }
        /*
         If no correct callback is registered, throw an exception.
         */
        if ((isMultiSelection && onMultiImageSelectedListener == null) ||
                (!isMultiSelection) && onSingleImageSelectedListener == null || ( showVideo && onVideoSelectedListener == null)) {
            throw new IllegalArgumentException("Your caller activity or parent fragment must implements the correct ImageSelectedListener");
        }
        return view;
    }

    /**
     * Here we make the bottom bar fade out when the Dialog is being slided down.
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                //Get the BottomSheetBehavior
                BottomSheetDialog d = (BottomSheetDialog) dialog;
                FrameLayout bottomSheet = d.findViewById(android.support.design.R.id.design_bottom_sheet);
                if (bottomSheet != null) {
                    bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
                    bottomSheetBehavior.setPeekHeight(peekHeight);
                    bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                        @Override
                        public void onStateChanged(@NonNull View bottomSheet, int newState) {
                            switch (newState) {
                                case BottomSheetBehavior.STATE_HIDDEN:
                                    dismiss();
                                    break;
                            }
                        }

                        @Override
                        public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                            if (bottomBarView != null) {
                                bottomBarView.setAlpha(slideOffset < 0 ? (1 + slideOffset) : 1);
                            }
                        }
                    });
                }
            }
        });

        return dialog;
    }

    /**
     * Here we create and setup the bottom bar if in multi-selection mode.
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (isMultiSelection) {
            setupBottomBar(getView());
        }
        if (savedInstanceState != null && adapter != null) {
            List<Uri> savedUriList = savedInstanceState.getParcelableArrayList("selectedImages");
            if (savedUriList != null) {
                List<File> fileList = new ArrayList<>();
                for (Uri each : savedUriList) {
                    File file = new File(URI.create(each.toString()));
                    fileList.add(file);
                }
                adapter.setSelectedFiles(fileList);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (getContext() == null) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }
        switch (requestCode) {
            case PERMISSION_READ_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLoaderManager().initLoader(LOADER_ID, null, this);
                } else {
                    dismiss();
                }
                break;
            case PERMISSION_CAMERA:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (Utils.isWriteStorageGranted(getContext())) {
                        launchCamera();
                    } else {
                        Utils.checkPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_WRITE_STORAGE);
                    }
                }
            case PERMISSION_WRITE_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (Utils.isCameraGranted(getContext())) {
                        launchCamera();
                    } else {
                        Utils.checkPermission(this, Manifest.permission.CAMERA, PERMISSION_CAMERA);
                    }
                }
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_TAKE_PHOTO:
                if (resultCode == RESULT_OK) {
                    notifyGallery();
                    if (onSingleImageSelectedListener != null) {
                        onSingleImageSelectedListener.onSingleImageSelected(currentPhotoUri);
                        dismiss();
                    }
                } else {
                    try {
                        File file = new File(URI.create(currentPhotoUri.toString()));
                        file.delete();
                    } catch (Exception e) {
                        if (BuildConfig.DEBUG)
                            Log.d("ImagePicker", "Failed to delete temp file: " + currentPhotoUri.toString());
                    }
                }
                break;
            case REQUEST_SELECT_FROM_GALLERY:
                if (resultCode == RESULT_OK) {
                    if (onSingleImageSelectedListener != null) {
                        onSingleImageSelectedListener.onSingleImageSelected(data.getData());
                        dismiss();
                    }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList("selectedImages", (ArrayList<Uri>) adapter.getSelectedUris());
        outState.putParcelable("currentPhotoUri", currentPhotoUri);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {


        if (id == LOADER_ID && getContext() != null) {
             CursorLoader cursorlist;
            String sortOrder =  MediaStore.Files.FileColumns.DATE_ADDED + " DESC" ;// Sort order.
            String selection;
            Uri uri = MediaStore.Files.getContentUri("external");
            //String[] projection = new String[]{MediaStore.Images.Media.DATA};
            String[] projection = {
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DATA,
                    MediaStore.Files.FileColumns.DATE_ADDED,
                    MediaStore.Files.FileColumns.MEDIA_TYPE,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.TITLE
            };

            if(showVideo) {
                // Return only video and image metadata.
                 selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                        + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                        + " OR "
                        + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                        + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
            }else{
                // Return only image metadata.
                 selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                        + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
            }
            cursorlist =  new CursorLoader(getContext(), uri, projection, selection, null, sortOrder);

            return cursorlist;
        } else {
            return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        //Log.i("onLoadFinished","loader getId = "+loader.getId());
        if (cursor != null) {
            List<File> uriList = new ArrayList<>();
            List<Boolean> isVideoList = new ArrayList<>();
            int index = 0;

            while (cursor.moveToNext() && index < maximumDisplayingImages) {
                String imagePath = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA));
                int type = cursor.getInt(cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE));
              //  Log.i("onLoadFinished","imagePath = "+imagePath);
                uriList.add(new File(imagePath));
                if(type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
                    isVideoList.add(true);
                else
                    isVideoList.add(false);
                index++;
            }
            cursor.moveToPosition(-1); //Restore cursor back to the beginning
            adapter.setImageList(uriList,isVideoList);
            //We are not closing the cursor here because Android Doc says Loader will manage them.
        }
    }

    @Override
    public void onLoaderReset(Loader loader) {
        adapter.setImageList(null,null);
    }

    private void loadConfigFromBuilder() {
        try {
            providerAuthority = getArguments().getString("providerAuthority");
            isMultiSelection = getArguments().getBoolean("isMultiSelect");
            maximumDisplayingImages = getArguments().getInt("maximumDisplayingImages");
            minimumMultiSelectCount = getArguments().getInt("minimumMultiSelectCount");
            maximumMultiSelectCount = getArguments().getInt("maximumMultiSelectCount");
            showCameraTile = getArguments().getBoolean("showCameraTile");
            showGalleryTile = getArguments().getBoolean("showGalleryTile");
            showVideo = getArguments().getBoolean("showVideo");
            spanCount = getArguments().getInt("spanCount");
            peekHeight = getArguments().getInt("peekHeight");
            gridSpacing = getArguments().getInt("gridSpacing");
            multiSelectBarBgColor = getArguments().getInt("multiSelectBarBgColor");
            multiSelectTextColor = getArguments().getInt("multiSelectTextColor");
            multiSelectDoneTextColor = getArguments().getInt("multiSelectDoneTextColor");
            showOverSelectMessage = getArguments().getBoolean("showOverSelectMessage");
            overSelectTextColor = getArguments().getInt("overSelectTextColor");
        } catch (Exception e) {
            if (BuildConfig.DEBUG) e.printStackTrace();
        }
    }

    private void bindViews(View rootView) {
        recyclerView = rootView.findViewById(R.id.picker_recyclerview);
    }

    private void setupRecyclerView() {
        GridLayoutManager gll = new GridLayoutManager(getContext(), spanCount);
        recyclerView.setLayoutManager(gll);
        /* We are disabling item change animation because the default animation is fade out fade in, which will
         * appear a little bit strange due to the fact that we are darkening the cell at the same time. */
        ((SimpleItemAnimator)recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        recyclerView.addItemDecoration(new GridItemSpacingDecoration(spanCount, gridSpacing, false));
        if (adapter == null) {
            adapter = new ImageTileAdapter(getContext(), isMultiSelection, showCameraTile, showGalleryTile,onSingleImageSelectedListener);
            adapter.setMaximumSelectionCount(maximumMultiSelectCount);
            adapter.setCameraTileOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (Utils.isCameraGranted(getContext()) && Utils.isWriteStorageGranted(getContext())) {
                        launchCamera();
                    } else {
                        if (Utils.isCameraGranted(getContext())) {
                            Utils.checkPermission(BSMultiPicker.this, Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_WRITE_STORAGE);
                        } else {
                            Utils.checkPermission(BSMultiPicker.this, Manifest.permission.CAMERA, PERMISSION_CAMERA);
                        }
                    }
                }
            });
            adapter.setGalleryTileOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isMultiSelection) {
                        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        startActivityForResult(intent, REQUEST_SELECT_FROM_GALLERY);
                    }
                }
            });
            adapter.setImageTileOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.getTag() != null && v.getTag() instanceof Uri && onSingleImageSelectedListener != null) {
                        onSingleImageSelectedListener.onSingleImageSelected((Uri) v.getTag());
                        dismiss();
                    }
                }
            });

            adapter.setVideoTileOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.getTag() != null && v.getTag() instanceof Uri && onVideoSelectedListener != null) {
                        onVideoSelectedListener.onVideoSelected((Uri) v.getTag());
                        dismiss();
                    }
                }
            });


            if (isMultiSelection) {
                adapter.setOnSelectedCountChangeListener(new ImageTileAdapter.OnSelectedCountChangeListener() {
                    @Override
                    public void onSelectedCountChange(int currentCount) {
                        updateSelectCount(currentCount);
                    }
                });
                adapter.setOnOverSelectListener(new ImageTileAdapter.OnOverSelectListener() {
                    @Override
                    public void onOverSelect() {
                        if (showOverSelectMessage) showOverSelectMessage();
                    }
                });
            }
        }
        recyclerView.setAdapter(adapter);
    }

    private void setupBottomBar(View rootView) {
        CoordinatorLayout parentView = (CoordinatorLayout) (rootView.getParent().getParent());
        bottomBarView = LayoutInflater.from(getContext()).inflate(R.layout.item_picker_multiselection_bar, parentView, false);
        ViewCompat.setTranslationZ(bottomBarView, ViewCompat.getZ((View) rootView.getParent()));
        parentView.addView(bottomBarView, -1);
        bottomBarView.findViewById(R.id.multiselect_bar_bg).setBackgroundColor(ContextCompat.getColor(getContext(), multiSelectBarBgColor));
        tvMultiSelectMessage = bottomBarView.findViewById(R.id.tv_multiselect_message);
        tvMultiSelectMessage.setTextColor(ContextCompat.getColor(getContext(), multiSelectTextColor));
       /* tvMultiSelectMessage.setText(minimumMultiSelectCount == 1 ?
                getString(R.string.imagepicker_multiselect_not_enough_singular) :
                getString(R.string.imagepicker_multiselect_not_enough_plural, minimumMultiSelectCount));*/
        tvMultiSelectMessage.setText(minimumMultiSelectCount == 1 ?
                showVideo? getString(R.string.imagepicker_multiselect_not_enough_singular_image_or_video):getString(R.string.imagepicker_multiselect_not_enough_singular) :
                getString(R.string.imagepicker_multiselect_not_enough_plural, minimumMultiSelectCount));
        tvDone = bottomBarView.findViewById(R.id.tv_multiselect_done);
        tvDone.setTextColor(ContextCompat.getColor(getContext(), multiSelectDoneTextColor));
        tvDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onMultiImageSelectedListener != null) {
                    onMultiImageSelectedListener.onMultiImageSelected(adapter.getSelectedUris());
                    dismiss();
                }
            }
        });
        tvDone.setAlpha(0.4f);
        tvDone.setEnabled(false);
    }

    public static void dismissImagePicker(){

    }
    private void launchCamera() {
        if (getContext() == null) return;
        Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePhotoIntent.resolveActivity(getContext().getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException e) {
                if (BuildConfig.DEBUG) e.printStackTrace();
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(getContext(),
                        providerAuthority,
                        photoFile);
                takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                List<ResolveInfo> resolvedIntentActivities = getContext().getPackageManager().queryIntentActivities(takePhotoIntent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolvedIntentInfo : resolvedIntentActivities) {
                    String packageName = resolvedIntentInfo.activityInfo.packageName;
                    getContext().grantUriPermission(packageName, photoURI, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                startActivityForResult(takePhotoIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",   /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoUri = Uri.fromFile(image);
        return image;
    }

    private void notifyGallery() {
        if (getContext() == null) return;
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(currentPhotoUri);
        getContext().sendBroadcast(mediaScanIntent);
    }

    private void updateSelectCount (int newCount) {
        if (getContext() == null) return;
        if (tvMultiSelectMessage != null) {
            tvMultiSelectMessage.setTextColor(ContextCompat.getColor(getContext(), multiSelectTextColor));
            if (newCount < minimumMultiSelectCount) {
                tvMultiSelectMessage.setText(minimumMultiSelectCount - newCount == 1 ?
                        getString(R.string.imagepicker_multiselect_not_enough_singular) :
                        getString(R.string.imagepicker_multiselect_not_enough_plural, minimumMultiSelectCount - newCount));
                tvDone.setAlpha(0.4f);
                tvDone.setEnabled(false);
            } else {
                tvMultiSelectMessage.setText(newCount == 1 ?
                        getString(R.string.imagepicker_multiselect_enough_singular) :
                        getString(R.string.imagepicker_multiselect_enough_plural, newCount));
                tvDone.setAlpha(1f);
                tvDone.setEnabled(true);
            }
        }
    }

    private void showOverSelectMessage () {
        if (tvMultiSelectMessage != null && getContext() != null) {
            tvMultiSelectMessage.setTextColor(ContextCompat.getColor(getContext(), overSelectTextColor));
            tvMultiSelectMessage.setText(getString(R.string.imagepicker_multiselect_overselect, maximumMultiSelectCount));
        }
    }

    /**
     * Builder of the BSMultiPicker.
     * Caller should always create the dialog using this builder.
     */
    public static class Builder {

        private String providerAuthority;
        private boolean isMultiSelect;
        private int maximumDisplayingImages = Integer.MAX_VALUE;
        private int minimumMultiSelectCount = 1;
        private int maximumMultiSelectCount = Integer.MAX_VALUE;
        private boolean showCameraTile = true;
        private boolean showGalleryTile = true;
        private boolean showVideo = false;
        private int peekHeight = Utils.dp2px(360);
        private int spanCount = 3;
        private int gridSpacing = Utils.dp2px(2);
        private int multiSelectBarBgColor = android.R.color.white;
        private int multiSelectTextColor = R.color.primary_text;
        private int multiSelectDoneTextColor = R.color.multiselect_done;
        private boolean showOverSelectMessage = true;
        private int overSelectTextColor = R.color.error_text;

        public Builder(String providerAuthority) {
            this.providerAuthority = providerAuthority;
        }

        public Builder showVideo (boolean showVideo) {
            this.showVideo = showVideo;
            return this;
        }

        public Builder isMultiSelect () {
            isMultiSelect = true;
            return this;
        }

        public Builder setMaximumDisplayingImages (int maximumDisplayingImages) {
            this.maximumDisplayingImages = maximumDisplayingImages;
            return this;
        }

        public Builder setMinimumMultiSelectCount(int minimumMultiSelectCount) {
            if (minimumMultiSelectCount <= 0) {
                throw new IllegalArgumentException("Minimum Multi Select Count must be >= 1");
            }
            this.minimumMultiSelectCount = minimumMultiSelectCount;
            return this;
        }

        public Builder setMaximumMultiSelectCount(int maximumMultiSelectCount) {
            if (maximumMultiSelectCount < 0) {
                throw new IllegalArgumentException("Maximum Multi Select Count must be > 0");
            }
            this.maximumMultiSelectCount = maximumMultiSelectCount;
            return this;
        }

        public Builder setGridSpacing(@Px int gridSpacing) {
            if (gridSpacing < 0) {
                throw new IllegalArgumentException("Grid spacing must be >= 0");
            }
            this.gridSpacing = gridSpacing;
            return this;
        }

        public Builder setMultiSelectBarBgColor(@ColorRes int multiSelectBarBgColor) {
            this.multiSelectBarBgColor = multiSelectBarBgColor;
            return this;
        }

        public Builder setMultiSelectDoneTextColor(@ColorRes int multiSelectDoneTextColor) {
            this.multiSelectDoneTextColor = multiSelectDoneTextColor;
            return this;
        }

        public Builder setMultiSelectTextColor(@ColorRes int multiSelectTextColor) {
            this.multiSelectTextColor = multiSelectTextColor;
            return this;
        }

        public Builder setOverSelectTextColor(@ColorRes int overSelectTextColor) {
            this.overSelectTextColor = overSelectTextColor;
            return this;
        }

        public Builder setPeekHeight(@Px int peekHeight) {
            if (peekHeight < 0) {
                throw new IllegalArgumentException("Peek Height must be >= 0");
            }
            this.peekHeight = peekHeight;
            return this;
        }

        public Builder hideCameraTile() {
            this.showCameraTile = false;
            return this;
        }

        public Builder hideGalleryTile() {
            this.showGalleryTile = false;
            return this;
        }

        public Builder disableOverSelectionMessage () {
            this.showOverSelectMessage = false;
            return this;
        }

        public Builder setSpanCount(int spanCount) {
            if (spanCount < 0) {
                throw new IllegalArgumentException("Span Count must be > 0");
            }
            this.spanCount = spanCount;
            return this;
        }



        public BSMultiPicker build() {
            Bundle args = new Bundle();
            args.putString("providerAuthority", providerAuthority);
            args.putBoolean("isMultiSelect", isMultiSelect);
            args.putInt("maximumDisplayingImages", maximumDisplayingImages);
            args.putInt("minimumMultiSelectCount", minimumMultiSelectCount);
            args.putInt("maximumMultiSelectCount", maximumMultiSelectCount);
            args.putBoolean("showCameraTile", showCameraTile);
            args.putBoolean("showGalleryTile", showGalleryTile);
            args.putBoolean("showVideo", showVideo);
            args.putInt("peekHeight", peekHeight);
            args.putInt("spanCount", spanCount);
            args.putInt("gridSpacing", gridSpacing);
            args.putInt("multiSelectBarBgColor", multiSelectBarBgColor);
            args.putInt("multiSelectTextColor", multiSelectTextColor);
            args.putInt("multiSelectDoneTextColor", multiSelectDoneTextColor);
            args.putBoolean("showOverSelectMessage", showOverSelectMessage);
            args.putInt("overSelectTextColor", overSelectTextColor);

            BSMultiPicker fragment = new BSMultiPicker();
            fragment.setArguments(args);
            return fragment;
        }

    }
}
