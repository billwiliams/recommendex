package com.paulormg.recommendex.server.services.user;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import com.paulormg.recommendex.client.services.user.UserService;
import com.paulormg.recommendex.server.dao.item.ItemDAO;
import com.paulormg.recommendex.server.dao.user.UserDAO;
import com.paulormg.recommendex.server.exception.DBException;
import com.paulormg.recommendex.server.util.ServerUtils;
import com.paulormg.recommendex.shared.exception.ExistingUserException;
import com.paulormg.recommendex.shared.exception.InvalidCredentialsException;
import com.paulormg.recommendex.shared.exception.ServerError;
import com.paulormg.recommendex.shared.transfer.User;
import com.paulormg.recommendex.shared.transfer.UserData;
import com.paulormg.recommendex.shared.util.FieldVerifier;

/**
 * The server side implementation of the RPC service.
 */
@SuppressWarnings("serial")
public class UserServiceImpl extends RemoteServiceServlet implements UserService {

	static Logger logger = Logger.getLogger(UserServiceImpl.class);	
	
	private UserDAO userDao = UserDAO.getInstance();
	private ItemDAO itemDao = ItemDAO.getInstance();
	
	@Override
	public UserData checkLoggedIn() throws ServerError {
		HttpSession session = this.getThreadLocalRequest().getSession(false);
		if (session == null)
			return null;
		
		User user = (User)session.getAttribute("user");
		
		try {
			return new UserData(user, itemDao.getAllItems(), false);
		} catch (DBException e) {
			throw new ServerError();
		}
	}	
	
	@Override
	public UserData login(String login, String password) throws InvalidCredentialsException, 
																IllegalArgumentException, 
																ServerError {
		
		if(!FieldVerifier.isValidLogin(login)) throw new IllegalArgumentException("Invalid login");
		if(!FieldVerifier.isValidPassword(login)) throw new IllegalArgumentException("Invalid login");
		
		boolean newUser = false;
		
		User user = null;
		try {
			user = userDao.authenticateUser(login, password);
		} catch (DBException e1) {
			throw new ServerError("Error while accessing DB");
		}
		
		if (user == null){
			newUser = true;
			try {
				user = userDao.createUser(login, password);
			} catch (DBException dbe){ 
				throw new ServerError();
			} catch (ExistingUserException e) {
				// race-condition: user with the same login was created in between
				throw new InvalidCredentialsException();
			}
		}

		
		HttpSession session = this.getThreadLocalRequest().getSession(true);		
		session.setAttribute("user", user);

		logger.info(String.format("User %s has logged in session %s.", 
									user.getLogin(), session.getId()));
		
		try {
			return new UserData(user, itemDao.getAllItems(), newUser);
		} catch (DBException e) {
			throw new ServerError();
		}
	}

	@Override
	public void logout(String login) throws InvalidCredentialsException {
		
		ServerUtils.authenticate(this.getThreadLocalRequest(), login);
		
		HttpServletRequest request = this.getThreadLocalRequest();
		HttpSession session = request.getSession(false);
		
		logger.info(String.format("User %s has logged out from session %s.", 
				login, session.getId()));		
		
		session.invalidate();
	}
}
