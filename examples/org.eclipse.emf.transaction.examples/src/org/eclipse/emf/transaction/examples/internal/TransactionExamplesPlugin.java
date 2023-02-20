/**
 * Copyright (c) 2006 IBM Corporation and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   IBM - Initial API and implementation
 */
package org.eclipse.emf.transaction.examples.internal;

import org.eclipse.ui.plugin.AbstractUIPlugin;


public class TransactionExamplesPlugin
	extends AbstractUIPlugin {

	// The shared instance.
	private static TransactionExamplesPlugin plugin;
	
	public TransactionExamplesPlugin() {
		super();
		plugin = this;
	}
	
	/**
	 * Returns the shared instance.
	 */
	public static TransactionExamplesPlugin getDefault() {
		return plugin;
	}
}
