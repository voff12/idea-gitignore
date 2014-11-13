/*
 * The MIT License (MIT)
 *
 * Copyright (c) today.year hsz Jakub Chrzanowski <jakub@hsz.mobi>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package mobi.hsz.idea.gitignore.daemon;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import mobi.hsz.idea.gitignore.GitignoreBundle;
import mobi.hsz.idea.gitignore.GitignoreLanguage;
import mobi.hsz.idea.gitignore.command.CreateFileCommandAction;
import mobi.hsz.idea.gitignore.ui.GeneratorDialog;
import mobi.hsz.idea.gitignore.util.Icons;
import mobi.hsz.idea.gitignore.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MissingGitignoreNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
    private static final Key<EditorNotificationPanel> KEY = Key.create(GitignoreBundle.message("daemon.missingGitignore.create"));
    private final Project project;
    private final EditorNotifications notifications;

    public MissingGitignoreNotificationProvider(Project project, @NotNull EditorNotifications notifications) {
        this.project = project;
        this.notifications = notifications;
    }

    @Override
    public Key<EditorNotificationPanel> getKey() {
        return KEY;
    }

    @Nullable
    @Override
    public EditorNotificationPanel createNotificationPanel(VirtualFile file, FileEditor fileEditor) {
        if (Properties.isIgnoreMissingGitignore(project)) {
            return null;
        }
        VirtualFile gitDirectory = project.getBaseDir().findChild(GitignoreLanguage.GIT_DIRECTORY);
        if (gitDirectory == null || !gitDirectory.isDirectory()) {
            return null;
        }
        VirtualFile gitignoreFile = project.getBaseDir().findChild(GitignoreLanguage.FILENAME);
        if (gitignoreFile != null) {
            return null;
        }

        return createPanel(project);
    }

    private EditorNotificationPanel createPanel(@NotNull final Project project) {
        final EditorNotificationPanel panel = new EditorNotificationPanel();
        panel.setText(GitignoreBundle.message("daemon.missingGitignore"));
        panel.createActionLabel(GitignoreBundle.message("daemon.missingGitignore.create"), new Runnable() {
            @Override
            public void run() {
                PsiDirectory directory = PsiManager.getInstance(project).findDirectory(project.getBaseDir());
                if (directory != null) {
                    PsiFile file = new CreateFileCommandAction(project, directory).execute().getResultObject();
                    FileEditorManager.getInstance(project).openFile(file.getVirtualFile(), true);
                    new GeneratorDialog(project, file).show();
                }
            }
        });
        panel.createActionLabel(GitignoreBundle.message("global.cancel"), new Runnable() {
            @Override
            public void run() {
                Properties.setIgnoreMissingGitignore(project);
                notifications.updateAllNotifications();
            }
        });

        try { // ignore if older SDK does not support panel icon
            panel.icon(Icons.FILE);
        } catch (NoSuchMethodError ignored) {}

        return panel;
    }
}
