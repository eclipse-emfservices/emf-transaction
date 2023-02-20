/**
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.workspace.tests.fixtures;

import org.eclipse.core.commands.operations.AbstractOperation;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

/**
 * A test operation that performs changes on external data in the form of a
 * string.
 *
 * @author Christian W. Damus (cdamus)
 */
public class ExternalDataOperation extends AbstractOperation {
	private String[] externalData;
	private String oldValue;
	private String newValue;
	
	public ExternalDataOperation(String[] externalData, String newValue) {
		super("Change External Data"); //$NON-NLS-1$
		
		this.externalData = externalData;
		this.newValue = newValue;
	}
	
	@Override
	public IStatus execute(IProgressMonitor monitor, IAdaptable info) {
		// change the external (non-EMF) data
		oldValue = externalData[0];
		externalData[0] = newValue;
		
		return Status.OK_STATUS;
	}
	
	@Override
	public IStatus undo(IProgressMonitor monitor, IAdaptable info) {
		externalData[0] = oldValue;
		
		return Status.OK_STATUS;
	}
	
	@Override
	public IStatus redo(IProgressMonitor monitor, IAdaptable info) {
		externalData[0] = newValue;
		
		return Status.OK_STATUS;
	}
}
