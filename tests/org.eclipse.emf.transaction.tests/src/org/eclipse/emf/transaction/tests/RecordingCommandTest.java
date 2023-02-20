/**
 * Copyright (c) 2009 SAP AG and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    SAP AG - initial API and implementation 
 */
package org.eclipse.emf.transaction.tests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;

/**
 * Tests for the recording command.
 * 
 * @author Boris Gruschko
 *
 */
public class RecordingCommandTest extends AbstractTest {
	
	public static Test suite() {
		return new TestSuite(RecordingCommandTest.class, "Recording Command Tests"); //$NON-NLS-1$
	}
	
	public void testPrePostDoExecute() {
		TransactionalEditingDomain domain = createEditingDomain(new ResourceSetImpl());
		
		_RecordingCommand cmd = new _RecordingCommand(domain);
		
		domain.getCommandStack().execute(cmd);
		
		assertEquals(1, cmd.preExecuteCalled);
		assertEquals(2, cmd.doExecuteCalled);
		assertEquals(3, cmd.postExecuteCalled);
	}
	
	private final class _RecordingCommand extends RecordingCommand {
		public int preExecuteCalled = 0;
		public int doExecuteCalled = 0;
		public int postExecuteCalled = 0;
		private int counter = 0;

		/**
		 * @param domain
		 */
		private _RecordingCommand(TransactionalEditingDomain domain) {
			super(domain);
		}

		@Override
		protected void preExecute() {
			preExecuteCalled = ++counter;
		}

		@Override
		protected void doExecute() {
			doExecuteCalled = ++counter;
		}

		@Override
		protected void postExecute() {
			postExecuteCalled = ++counter;
		}
	}
}
