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

package mobi.hsz.idea.gitignore.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import mobi.hsz.idea.gitignore.psi.GitignoreEntry;
import mobi.hsz.idea.gitignore.util.Glob;
import mobi.hsz.idea.gitignore.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class GitignoreReferenceSet extends FileReferenceSet {
    public GitignoreReferenceSet(@NotNull GitignoreEntry element) {
        super(element);
    }

    @Override
    public FileReference createFileReference(TextRange range, int index, String text) {
        return new GitignoreReference(this, range, index, text);
    }

    @Override
    public boolean isEndingSlashNotAllowed() {
        return false;
    }

    @NotNull
    @Override
    public Collection<PsiFileSystemItem> computeDefaultContexts() {
        PsiFile containingFile = getElement().getContainingFile();
        PsiDirectory containingDirectory = containingFile.getParent();
        return containingDirectory != null ? Collections.<PsiFileSystemItem>singletonList(containingDirectory) : super.computeDefaultContexts();
    }

    @Nullable
    public FileReference getLastReference() {
        FileReference lastReference = super.getLastReference();
        if (lastReference != null && lastReference.getCanonicalText().endsWith(getSeparatorString())) {
            return this.myReferences != null && this.myReferences.length > 1 ? this.myReferences[this.myReferences.length - 2] : null;
        }
        return lastReference;
    }

    @Override
    public boolean couldBeConvertedTo(boolean relative) {
        return false;
    }

    @Override
    protected void reparse() {
        String str = StringUtil.trimEnd(getPathString(), getSeparatorString());
        final List<FileReference> referencesList = new ArrayList<FileReference>();

        String separatorString = getSeparatorString(); // separator's length can be more then 1 char
        int sepLen = separatorString.length();
        int currentSlash = -sepLen;
        int startInElement = getStartInElement();

        // skip white space
        while (currentSlash + sepLen < str.length() && Character.isWhitespace(str.charAt(currentSlash + sepLen))) {
            currentSlash++;
        }

        if (currentSlash + sepLen + sepLen < str.length() &&
                str.substring(currentSlash + sepLen, currentSlash + sepLen + sepLen).equals(separatorString)) {
            currentSlash += sepLen;
        }
        int index = 0;

        if (str.equals(separatorString)) {
            final FileReference fileReference =
                    createFileReference(new TextRange(startInElement, startInElement + sepLen), index++, separatorString);
            referencesList.add(fileReference);
        }

        while (true) {
            final int nextSlash = str.indexOf(separatorString, currentSlash + sepLen);
            final String subReferenceText = nextSlash > 0 ? str.substring(0, nextSlash) : str.substring(0);
            TextRange range = new TextRange(startInElement + currentSlash + sepLen, startInElement + (nextSlash > 0 ? nextSlash : str.length()));
            final FileReference ref = createFileReference(range, index++, subReferenceText);
            referencesList.add(ref);
            if ((currentSlash = nextSlash) < 0) {
                break;
            }
        }

        myReferences = referencesList.toArray(new FileReference[referencesList.size()]);
    }

    private class GitignoreReference extends FileReference {
        public GitignoreReference(@NotNull FileReferenceSet fileReferenceSet, TextRange range, int index, String text) {
            super(fileReferenceSet, range, index, text);
        }

        @Override
        protected void innerResolveInContext(@NotNull String text, @NotNull PsiFileSystemItem context, final Collection<ResolveResult> result, boolean caseSensitive) {
            super.innerResolveInContext(text, context, result, caseSensitive);
            VirtualFile contextVirtualFile = context.getVirtualFile();
            if (contextVirtualFile != null) {
                final Pattern pattern = Glob.createPattern(getCanonicalText());
                if (pattern != null) {
                    PsiDirectory parent = getElement().getContainingFile().getParent();
                    final VirtualFile root = (parent != null) ? parent.getVirtualFile() : null;
                    final PsiManager manager = getElement().getManager();

                    VfsUtil.visitChildrenRecursively(contextVirtualFile, new VirtualFileVisitor(VirtualFileVisitor.NO_FOLLOW_SYMLINKS) {
                        @Override
                        public boolean visitFile(@NotNull VirtualFile file) {
                            String name = (root != null) ? Utils.getRelativePath(root, file) : file.getName();
                            if (name == null || Utils.isGitDirectory(name)) {
                                return false;
                            }
                            PsiFileSystemItem psiFileSystemItem = getPsiFileSystemItem(manager, file);
                            if (psiFileSystemItem == null) {
                                return false;
                            }
                            if (pattern.matcher(name).matches()) {
                                result.add(new PsiElementResolveResult(psiFileSystemItem));
                            }
                            return true;
                        }
                    });
                }
            }
        }

        private PsiFileSystemItem getPsiFileSystemItem(PsiManager manager, @NotNull VirtualFile file) {
            return file.isDirectory() ? manager.findDirectory(file) : manager.findFile(file);
        }
    }
}
