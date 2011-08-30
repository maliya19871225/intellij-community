package com.jetbrains.python.refactoring.classes.pushDown;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.refactoring.classes.PyMemberInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class PyPushDownConflicts {
  private static final Logger LOG = Logger.getInstance(PyPushDownProcessor.class.getName());

  private final PyClass myClass;
  private final Collection<PyMemberInfo> myMembers;
  private final MultiMap<PsiElement, String> myConflicts;

  public PyPushDownConflicts(final PyClass clazz, final Collection<PyMemberInfo> members) {
    myClass = clazz;
    myMembers = members;
    myConflicts = new MultiMap<PsiElement, String>();
  }

  public MultiMap<PsiElement, String> getConflicts() {
    return myConflicts;
  }

  public void checkTargetClassConflicts(PyClass clazz) {
    checkPlacementConflicts(clazz);    
  }

  private void checkPlacementConflicts(PyClass clazz) {
    for (PyMemberInfo member : myMembers) {
      final PyElement element = member.getMember();
      if (element instanceof PyFunction) {
        for (PyFunction function : clazz.getMethods()) {
          if (Comparing.strEqual(function.getName(), element.getName())) {
             final String message = RefactoringBundle.message("0.is.already.overridden.in.1",
                                                 RefactoringUIUtil.getDescription(element, false),
                                                 RefactoringUIUtil.getDescription(clazz, false));
             myConflicts.putValue(element, message);
          }
        }
      } else if (element instanceof PyClass) {
      } else LOG.error("unmatched member class " + clazz.getClass());
    }
  }

  public void checkSourceClassConflicts() {
    final List<PyElement> elements = ContainerUtil.map(myMembers, new Function<PyMemberInfo, PyElement>() {
      public PyElement fun(PyMemberInfo pyMemberInfo) {
        return pyMemberInfo.getMember();
      }
    });
    for (PyFunction pyFunction : myClass.getMethods()) {
      final UsedMembersCollector collector = new UsedMembersCollector(elements);
      pyFunction.accept(collector);
      final List<PyElement> conflicts = collector.getCollection();

      for (PyElement conflict : conflicts) {
        final String message = RefactoringBundle.message("0.uses.1.which.is.pushed.down",
                                                 RefactoringUIUtil.getDescription(pyFunction, false),
                                                 RefactoringUIUtil.getDescription(conflict, false));
        myConflicts.putValue(pyFunction, message);
      }
    }
  }

  private static class UsedMembersCollector extends PyRecursiveElementVisitor {
    private final List<PyElement> myCollection = new ArrayList<PyElement>();
    private final Collection<PyElement> myMovedMembers;

    private UsedMembersCollector(Collection<PyElement> movedMembers) {
      myMovedMembers = movedMembers;
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      final PyCallExpression.PyMarkedCallee markedFunction = node.resolveCallee(PyResolveContext.noImplicits());
      final Callable function = markedFunction != null ? markedFunction.getCallable() : null;
      if (myMovedMembers.contains(function)) {
        myCollection.add(function);
      }
    }

    public List<PyElement> getCollection() {
      return myCollection;
    }
  }
}
