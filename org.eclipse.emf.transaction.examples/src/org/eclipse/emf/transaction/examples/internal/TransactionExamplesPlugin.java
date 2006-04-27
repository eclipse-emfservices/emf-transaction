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
