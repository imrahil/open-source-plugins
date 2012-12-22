package utils;

import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageInfoToUsageConverter;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: John Lindquist
 * Date: 6/23/11
 * Time: 5:44 PM
 */
public class FindUsagesUtils
{
    public static void findUsagesOfPsiElement(PsiElement psiElement, Project project, final List<UsageInfo2UsageAdapter> usages, Runnable runnable)
    {
        Processor<Usage> collect = new Processor<Usage>()
        {
            public boolean process(@NotNull Usage usage)
            {
                synchronized (usages)
                {
                    usages.add(((UsageInfo2UsageAdapter) usage));
                }
                return true;
            }
        };

        FindUsagesManager findUsagesManager = ((FindManagerImpl) FindManager.getInstance(project)).getFindUsagesManager();
        FindUsagesHandler findUsagesHandler = findUsagesManager.getFindUsagesHandler(psiElement, false);
        FindUsagesOptions findUsagesOptions = findUsagesHandler.getFindUsagesOptions();
        UsageInfoToUsageConverter.TargetElementsDescriptor descriptor = new UsageInfoToUsageConverter.TargetElementsDescriptor(findUsagesHandler.getPrimaryElements(), findUsagesHandler.getSecondaryElements());

        FindUsagesManager.startProcessUsages(findUsagesHandler, descriptor, collect, findUsagesOptions, runnable);
    }
}
