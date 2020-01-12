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

import org.eclipse.emf.common.command.AbstractCommand;

/**
 * A test operation that performs changes on external data in the form of a
 * string.
 *
 * @author Christian W. Damus (cdamus)
 */
public class ExternalDataCommand extends AbstractCommand {
	private String[] externalData;
	private String oldValue;
	private String newValue;
	
	public ExternalDataCommand(String[] externalData, String newValue) {
		super("Change External Data"); //$NON-NLS-1$
		
		this.externalData = externalData;
		this.newValue = newValue;
	}
	
	@Override
	protected boolean prepare() {
		return true;
	}
	
	public void execute() {
		// change the external (non-EMF) data
		oldValue = externalData[0];
		externalData[0] = newValue;
	}
	
	@Override
	public void undo() {
		externalData[0] = oldValue;
	}
	
	public void redo() {
		externalData[0] = newValue;
	}
}
