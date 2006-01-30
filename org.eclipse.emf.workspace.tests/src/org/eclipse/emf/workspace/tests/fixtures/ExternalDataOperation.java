/**
 * <copyright>
 *
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *
 * </copyright>
 *
 * $Id: ExternalDataOperation.java,v 1.1 2006/01/30 16:26:01 cdamus Exp $
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
	
	public IStatus execute(IProgressMonitor monitor, IAdaptable info) {
		// change the external (non-EMF) data
		oldValue = externalData[0];
		externalData[0] = newValue;
		
		return Status.OK_STATUS;
	}
	
	public IStatus undo(IProgressMonitor monitor, IAdaptable info) {
		externalData[0] = oldValue;
		
		return Status.OK_STATUS;
	}
	
	public IStatus redo(IProgressMonitor monitor, IAdaptable info) {
		externalData[0] = newValue;
		
		return Status.OK_STATUS;
	}
}
