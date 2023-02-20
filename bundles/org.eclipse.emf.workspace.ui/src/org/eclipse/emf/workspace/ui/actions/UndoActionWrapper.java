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
package org.eclipse.emf.workspace.ui.actions;

import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.emf.edit.ui.action.UndoAction;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.operations.UndoActionHandler;

/**
 * Extension of the EMF {@link UndoAction} class that delegates undo behaviour
 * to the undoable operation framework's {@link UndoActionHandler}.
 *
 * @author Christian W. Damus (cdamus)
 */
public class UndoActionWrapper extends UndoAction {
	private final ActionWrapperHelper<UndoActionHandler> delegate;
	
	/**
	 * Initializes me.
	 */
	public UndoActionWrapper() {
		delegate = new ActionWrapperHelper<UndoActionHandler>(
				new ActionWrapperHelper.OwnerAccess<UndoActionHandler>() {
		
			public void firePropertyChange(String property, Object oldValue,
					Object newValue) {
				firePropertyChange0(property, oldValue, newValue);
			}
		
			public UndoActionHandler createDelegate(
					IWorkbenchPartSite site, IUndoContext context) {
				return new UndoActionHandler(site, context);
			}});
	}

	// method defined to give the inner listener class access to inherited protected method
	void firePropertyChange0(String property, Object oldValue, Object newValue) {
		firePropertyChange(property, oldValue, newValue);
	}
	
	/**
	 * Extends the superclass implementation to update the operation history
	 * undo action handler to which I delegate.
	 */
	@Override
	public void setActiveWorkbenchPart(IWorkbenchPart workbenchPart) {
		super.setActiveWorkbenchPart(workbenchPart);
		delegate.setActiveWorkbenchPart(workbenchPart);
	}
	
	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public void update() {
		if (delegate != null) {
			delegate.update();
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public String getDescription() {
		if (delegate != null) {
			return delegate.getDescription();
		} else {
			return null;
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public String getText() {
		if (delegate != null) {
			return delegate.getText();
		} else {
			return null;
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public String getToolTipText() {
		if (delegate != null) {
			return delegate.getToolTipText();
		} else {
			return null;
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public boolean isEnabled() {
		if (delegate != null) {
			return delegate.isEnabled();
		} else {
			return false;
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public boolean isHandled() {
		if (delegate != null) {
			return delegate.isHandled();
		} else {
			return false;
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public void run() {
		if (delegate != null) {
			delegate.run();
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public void runWithEvent(Event event) {
		if (delegate != null) {
			delegate.runWithEvent(event);
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public void setChecked(boolean checked) {
		if (delegate != null) {
			delegate.setChecked(checked);
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public void setDescription(String text) {
		if (delegate != null) {
			delegate.setDescription(text);
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public void setEnabled(boolean enabled) {
		if (delegate != null) {
			delegate.setEnabled(enabled);
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public void setText(String text) {
		if (delegate != null) {
			delegate.setText(text);
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	@Override
	public void setToolTipText(String toolTipText) {
		if (delegate != null) {
			delegate.setToolTipText(toolTipText);
		}
	}
}
