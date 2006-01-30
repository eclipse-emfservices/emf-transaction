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
 * $Id: UndoActionWrapper.java,v 1.1 2006/01/30 16:20:11 cdamus Exp $
 */

package org.eclipse.emf.workspace.ui.actions;

import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.emf.edit.ui.action.UndoAction;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.operations.UndoActionHandler;

/**
 * Extension of the EMF {@link UndoAction} class that delegates undo behaviour
 * to the undoable operation framework's {@link UndoActionHandler}.
 *
 * @author Christian W. Damus (cdamus)
 */
public class UndoActionWrapper extends UndoAction {
	private UndoActionHandler delegate;
	
	private final IPropertyChangeListener listener = new IPropertyChangeListener() {
		public void propertyChange(PropertyChangeEvent event) {
			// propagate to my own listeners
			firePropertyChange0(event.getProperty(), event.getOldValue(), event.getNewValue());
		}};
	
	/**
	 * Initializes me.
	 */
	public UndoActionWrapper() {
		super();
	}

	// method defined to give the inner listener class access to inherited protected method
	void firePropertyChange0(String property, Object oldValue, Object newValue) {
		firePropertyChange(property, oldValue, newValue);
	}
	
	/**
	 * Extends the superclass implementation to update the operation history
	 * undo action handler to which I delegate.
	 */
	public void setActiveWorkbenchPart(IWorkbenchPart workbenchPart) {
		super.setActiveWorkbenchPart(workbenchPart);
		
		IUndoContext context = null;
		
		if (workbenchPart != null) {
			context = (IUndoContext) workbenchPart.getAdapter(IUndoContext.class);
		}
		
		if (context != null) {
			if (delegate != null) {
				delegate.removePropertyChangeListener(listener);
				delegate.dispose();
			}
			
			delegate = new UndoActionHandler(workbenchPart.getSite(), context);
			delegate.addPropertyChangeListener(listener);
			
			// force enablement update in UI
			boolean enabled = isEnabled();
			firePropertyChange0(
					IAction.ENABLED,
					Boolean.valueOf(!enabled),
					Boolean.valueOf(enabled));
		}
	}
	
	/**
	 * Delegates to the operation framework action handler.
	 */
	public void update() {
		if (delegate != null) {
			delegate.update();
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
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
	public void run() {
		if (delegate != null) {
			delegate.run();
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	public void runWithEvent(Event event) {
		if (delegate != null) {
			delegate.runWithEvent(event);
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	public void setChecked(boolean checked) {
		if (delegate != null) {
			delegate.setChecked(checked);
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	public void setDescription(String text) {
		if (delegate != null) {
			delegate.setDescription(text);
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	public void setEnabled(boolean enabled) {
		if (delegate != null) {
			delegate.setEnabled(enabled);
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	public void setText(String text) {
		if (delegate != null) {
			delegate.setText(text);
		}
	}

	/**
	 * Delegates to the operation framework action handler.
	 */
	public void setToolTipText(String toolTipText) {
		if (delegate != null) {
			delegate.setToolTipText(toolTipText);
		}
	}
}
