/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.indachat.messenger.camera;

import android.hardware.Camera;

import java.util.ArrayList;

public class CameraInfo {

    protected int cameraId;
    protected Camera camera;
    protected ArrayList<Size> pictureSizes = new ArrayList<>();
    protected ArrayList<Size> previewSizes = new ArrayList<>();
    protected final int frontCamera;

    public CameraInfo(int id, int frontFace) {
        cameraId = id;
        frontCamera = frontFace;
    }

    public int getCameraId() {
        return cameraId;
    }

    private Camera getCamera() {
        return camera;
    }

    public ArrayList<Size> getPreviewSizes() {
        return previewSizes;
    }

    public ArrayList<Size> getPictureSizes() {
        return pictureSizes;
    }

    public boolean isFrontface() {
        return frontCamera != 0;
    }
}
