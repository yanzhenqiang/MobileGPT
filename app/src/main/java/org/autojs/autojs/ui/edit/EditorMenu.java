package org.autojs.autojs.ui.edit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.snackbar.Snackbar;
import com.stardust.autojs.script.JavaScriptSource;
import com.stardust.pio.PFiles;
import com.stardust.util.ClipboardUtil;
import com.stardust.util.IntentUtil;

import org.autojs.autoxjs.R;
import org.autojs.autojs.model.indices.AndroidClass;
import org.autojs.autojs.model.indices.ClassSearchingItem;
import org.autojs.autojs.theme.dialog.ThemeColorMaterialDialogBuilder;
import org.autojs.autojs.ui.common.NotAskAgainDialog;
import org.autojs.autojs.ui.edit.editor.CodeEditor;
import org.autojs.autojs.ui.log.LogActivityKt;
import org.autojs.autojs.ui.build.BuildActivity;

import java.util.Locale;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;

/**
 * Created by Stardust on 2017/9/28.
 */

@SuppressLint("CheckResult")
public class EditorMenu {

    private EditorView mEditorView;
    private Context mContext;
    private CodeEditor mEditor;

    public EditorMenu(EditorView editorView) {
        mEditorView = editorView;
        mContext = editorView.getContext();
        mEditor = editorView.getEditor();
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_log:
                showLog();
                return true;
            case R.id.action_force_stop:
                forceStop();
                return true;
            default:
                if (onEditOptionsSelected(item)) {
                    return true;
                }
                if (onMoreOptionsSelected(item)) {
                    return true;
                }
        }
        return false;
    }


    private boolean onMoreOptionsSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_console:
                showConsole();
                return true;
            case R.id.action_import_java_class:
                importJavaPackageOrClass();
                return true;
            case R.id.action_editor_theme:
                mEditorView.selectEditorTheme();
                return true;
            case R.id.action_open_by_other_apps:
                openByOtherApps();
                return true;
            case R.id.action_info:
                showInfo();
                return true;
            case R.id.action_build_apk:
                startBuildApkActivity();
                return true;

        }
        return false;
    }

    private void importJavaPackageOrClass() {
        mEditor.getSelection()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(s ->
                        new ClassSearchDialogBuilder(mContext)
                                .setQuery(s)
                                .itemClick((dialog, item, pos) -> showClassSearchingItem(dialog, item))
                                .title(R.string.text_search_java_class)
                                .show()
                );
    }

    private void showClassSearchingItem(MaterialDialog dialog, ClassSearchingItem item) {
        String title;
        String desc;
        if (item instanceof ClassSearchingItem.ClassItem) {
            AndroidClass androidClass = ((ClassSearchingItem.ClassItem) item).getAndroidClass();
            title = androidClass.getClassName();
            desc = androidClass.getFullName();
        } else {
            title = ((ClassSearchingItem.PackageItem) item).getPackageName();
            desc = title;
        }
        new ThemeColorMaterialDialogBuilder(mContext)
                .title(title)
                .content(desc)
                .positiveText(R.string.text_copy)
                .negativeText(R.string.text_en_import)
                .neutralText(R.string.text_view_docs)
                .onPositive((ignored, which) -> {
                    ClipboardUtil.setClip(mContext, desc);
                    Toast.makeText(mContext, R.string.text_already_copy_to_clip, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .onNegative((ignored, which) -> {
                    if (mEditor.getText().startsWith(JavaScriptSource.EXECUTION_MODE_UI_PREFIX)) {
                        mEditor.insert(1, item.getImportText() + ";\n");
                    } else {
                        mEditor.insert(0, item.getImportText() + ";\n");
                    }
                })
                .onNeutral((ignored, which) -> IntentUtil.browse(mContext, item.getUrl()))
                .onAny((ignored, which) -> dialog.dismiss())
                .show();
    }

    private void startBuildApkActivity() {
        BuildActivity.Companion.start(mContext,mEditorView.getUri().getPath());
    }


    private boolean onEditOptionsSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_find_or_replace:
                findOrReplace();
                return true;
            case R.id.action_copy_all:
                copyAll();
                return true;
            case R.id.action_copy_line:
                copyLine();
                return true;
            case R.id.action_delete_line:
                deleteLine();
                return true;
            case R.id.action_clear:
                mEditor.setText("");
                return true;
            case R.id.action_beautify:
                beautifyCode();
                return true;
        }
        return false;
    }

    private void showInfo() {
        Observable.zip(Observable.just(mEditor.getText()), mEditor.getLineCount(), (text, lineCount) -> {
                    String size = PFiles.getHumanReadableSize(text.length());
                    return String.format(Locale.getDefault(), mContext.getString(R.string.format_editor_info),
                            text.length(), lineCount, size);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showInfo);

    }

    private void showInfo(String info) {
        new ThemeColorMaterialDialogBuilder(mContext)
                .title(R.string.text_info)
                .content(info)
                .show();
    }

    private void copyLine() {
        mEditor.copyLine();
    }


    private void deleteLine() {
        mEditor.deleteLine();
    }

    private void paste() {
        mEditor.insert(ClipboardUtil.getClip(mContext).toString());
    }

    private void findOrReplace() {
        mEditor.getSelection()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(s ->
                        new FindOrReplaceDialogBuilder(mContext, mEditorView)
                                .setQueryIfNotEmpty(s)
                                .show()
                );

    }

    private void copyAll() {
        ClipboardUtil.setClip(mContext, mEditor.getText());
        Snackbar.make(mEditorView, R.string.text_already_copy_to_clip, Snackbar.LENGTH_SHORT).show();
    }


    private void showLog() {
        LogActivityKt.start(mContext);
    }

    private void showConsole() {
        mEditorView.showConsole();
    }

    private void forceStop() {
        mEditorView.forceStop();
    }

    private void openByOtherApps() {
        mEditorView.openByOtherApps();
    }

    private void beautifyCode() {
        mEditorView.beautifyCode();
    }

}
