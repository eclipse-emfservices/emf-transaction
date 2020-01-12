/**
 * Copyright (c) 2006, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.transaction.examples.internal.wizard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.transaction.examples.internal.TransactionExamplesPlugin;


public class WorkspaceExampleWizard
	extends AbstractExampleWizard {
	
	@Override
	protected Collection<ProjectDescriptor> getProjectDescriptors() {
		// We need the workspace example to be unzipped along with the
		// EMF library example model, edit and editor examples
		List<ProjectDescriptor> projects = new ArrayList<ProjectDescriptor>(4);
		projects.add(new ProjectDescriptor("org.eclipse.emf.transaction.examples", "zips/library.zip", "org.eclipse.emf.examples.library"));  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
		projects.add(new ProjectDescriptor("org.eclipse.emf.transaction.examples", "zips/libraryEdit.zip", "org.eclipse.emf.examples.library.edit")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		projects.add(new ProjectDescriptor("org.eclipse.emf.transaction.examples", "zips/libraryEditor.zip", "org.eclipse.emf.examples.library.editor"));  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
		projects.add(new ProjectDescriptor("org.eclipse.emf.transaction.examples", "zips/workspace.zip", "org.eclipse.emf.transaction.examples.workspace")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		
		return projects;
	}
	
	@Override
	protected void log(Exception e) {
		if (e instanceof CoreException) {
			TransactionExamplesPlugin.getDefault().getLog().log(((CoreException)e).getStatus());
		} else {
			TransactionExamplesPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, TransactionExamplesPlugin.getDefault().getBundle().getSymbolicName(),IStatus.ERROR, e.getMessage(),e));
		}
	}
}
