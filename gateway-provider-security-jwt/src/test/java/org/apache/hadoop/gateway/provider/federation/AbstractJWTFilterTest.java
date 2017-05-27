/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.gateway.provider.federation;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.security.AccessController;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Date;
import java.util.Set;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.hadoop.gateway.provider.federation.jwt.filter.AbstractJWTFilter;
import org.apache.hadoop.gateway.provider.federation.jwt.filter.SSOCookieFederationFilter;
import org.apache.hadoop.gateway.security.PrimaryPrincipal;
import org.apache.hadoop.gateway.services.security.token.JWTokenAuthority;
import org.apache.hadoop.gateway.services.security.token.TokenServiceException;
import org.apache.hadoop.gateway.services.security.token.impl.JWT;
import org.apache.hadoop.gateway.services.security.token.impl.JWTToken;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.nimbusds.jose.*;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.util.Base64URL;

public abstract class AbstractJWTFilterTest  {
  private static final String SERVICE_URL = "https://localhost:8888/resource";
  
  protected AbstractJWTFilter handler = null;
  protected RSAPublicKey publicKey = null;
  protected RSAPrivateKey privateKey = null;

  protected abstract void setTokenOnRequest(HttpServletRequest request, SignedJWT jwt);
  protected abstract String getAudienceProperty();

  @Before
  public void setup() throws Exception, NoSuchAlgorithmException {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);

    KeyPair kp = kpg.genKeyPair();
    publicKey = (RSAPublicKey) kp.getPublic();
    privateKey = (RSAPrivateKey) kp.getPrivate();
  }

  @After
  public void teardown() throws Exception {
    handler.destroy();
  }
  
  @Test
  public void testValidJWT() throws Exception {
    try {
      Properties props = getProperties();
      handler.init(new TestFilterConfig(props));

      SignedJWT jwt = getJWT("alice", new Date(new Date().getTime() + 5000), privateKey);

      HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
      setTokenOnRequest(request, jwt);
      
      EasyMock.expect(request.getRequestURL()).andReturn(
          new StringBuffer(SERVICE_URL)).anyTimes();
      EasyMock.expect(request.getQueryString()).andReturn(null);
      HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
      EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(
          SERVICE_URL);
      EasyMock.replay(request);

      TestFilterChain chain = new TestFilterChain();
      handler.doFilter(request, response, chain);
      Assert.assertTrue("doFilterCalled should not be false.", chain.doFilterCalled == true);
      Set<PrimaryPrincipal> principals = chain.subject.getPrincipals(PrimaryPrincipal.class);
      Assert.assertTrue("No PrimaryPrincipal", principals.size() > 0);
      Assert.assertEquals("Not the expected principal", "alice", ((Principal)principals.toArray()[0]).getName());
    } catch (ServletException se) {
      fail("Should NOT have thrown a ServletException.");
    }
  }
  
  @Test
  public void testValidAudienceJWT() throws Exception {
    try {
      Properties props = getProperties();
      props.put(getAudienceProperty(), "bar");
      handler.init(new TestFilterConfig(props));

      SignedJWT jwt = getJWT("alice", new Date(new Date().getTime() + 5000), privateKey);

      HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
      setTokenOnRequest(request, jwt);
      
      EasyMock.expect(request.getRequestURL()).andReturn(
          new StringBuffer(SERVICE_URL)).anyTimes();
      EasyMock.expect(request.getQueryString()).andReturn(null);
      HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
      EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(
          SERVICE_URL);
      EasyMock.replay(request);

      TestFilterChain chain = new TestFilterChain();
      handler.doFilter(request, response, chain);
      Assert.assertTrue("doFilterCalled should not be false.", chain.doFilterCalled == true);
      Set<PrimaryPrincipal> principals = chain.subject.getPrincipals(PrimaryPrincipal.class);
      Assert.assertTrue("No PrimaryPrincipal", principals.size() > 0);
      Assert.assertEquals("Not the expected principal", "alice", ((Principal)principals.toArray()[0]).getName());
    } catch (ServletException se) {
      fail("Should NOT have thrown a ServletException.");
    }
  }
  
  @Test
  public void testInvalidAudienceJWT() throws Exception {
    try {
      Properties props = getProperties();
      props.put(getAudienceProperty(), "foo");
      handler.init(new TestFilterConfig(props));

      SignedJWT jwt = getJWT("alice", new Date(new Date().getTime() + 5000), privateKey);

      HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
      setTokenOnRequest(request, jwt);
      
      EasyMock.expect(request.getRequestURL()).andReturn(
          new StringBuffer(SERVICE_URL)).anyTimes();
      EasyMock.expect(request.getQueryString()).andReturn(null);
      HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
      EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(
          SERVICE_URL);
      EasyMock.replay(request);

      TestFilterChain chain = new TestFilterChain();
      handler.doFilter(request, response, chain);
      Assert.assertTrue("doFilterCalled should not be false.", chain.doFilterCalled == false);
      Assert.assertTrue("No Subject should be returned.", chain.subject == null);
    } catch (ServletException se) {
      fail("Should NOT have thrown a ServletException.");
    }
  }

  @Test
  public void testExpiredJWT() throws Exception {
    try {
      Properties props = getProperties();
      handler.init(new TestFilterConfig(props));

      SignedJWT jwt = getJWT("alice", new Date(new Date().getTime() - 1000), privateKey);

      HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
      setTokenOnRequest(request, jwt);
      
      EasyMock.expect(request.getRequestURL()).andReturn(
          new StringBuffer(SERVICE_URL)).anyTimes();
      EasyMock.expect(request.getQueryString()).andReturn(null);
      HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
      EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(
          SERVICE_URL);
      EasyMock.replay(request);

      TestFilterChain chain = new TestFilterChain();
      handler.doFilter(request, response, chain);
      Assert.assertTrue("doFilterCalled should not be false.", chain.doFilterCalled == false);
      Assert.assertTrue("No Subject should be returned.", chain.subject == null);
    } catch (ServletException se) {
      fail("Should NOT have thrown a ServletException.");
    }
  }
  
  @Test
  public void testValidJWTNoExpiration() throws Exception {
    try {
      Properties props = getProperties();
      handler.init(new TestFilterConfig(props));

      SignedJWT jwt = getJWT("alice", null, privateKey);

      HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
      setTokenOnRequest(request, jwt);
      
      EasyMock.expect(request.getRequestURL()).andReturn(
          new StringBuffer(SERVICE_URL)).anyTimes();
      EasyMock.expect(request.getQueryString()).andReturn(null);
      HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
      EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(
          SERVICE_URL).anyTimes();
      EasyMock.replay(request);

      TestFilterChain chain = new TestFilterChain();
      handler.doFilter(request, response, chain);
      Assert.assertTrue("doFilterCalled should not be false.", chain.doFilterCalled == true);
      Set<PrimaryPrincipal> principals = chain.subject.getPrincipals(PrimaryPrincipal.class);
      Assert.assertTrue("No PrimaryPrincipal", principals.size() > 0);
      Assert.assertEquals("Not the expected principal", "alice", ((Principal)principals.toArray()[0]).getName());
    } catch (ServletException se) {
      fail("Should NOT have thrown a ServletException.");
    }
  }
  
  protected Properties getProperties() {
    Properties props = new Properties();
    props.setProperty(
        SSOCookieFederationFilter.SSO_AUTHENTICATION_PROVIDER_URL,
        "https://localhost:8443/authserver");
    return props;
  }

  protected SignedJWT getJWT(String sub, Date expires, RSAPrivateKey privateKey)
      throws Exception {
    List<String> aud = new ArrayList<String>();
    aud.add("bar");

    JWTClaimsSet claims = new JWTClaimsSet.Builder()
    .issuer("KNOXSSO")
    .subject(sub)
    .audience(aud)
    .expirationTime(expires)
    .claim("scope", "openid")
    .build();

    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).build();

    SignedJWT signedJWT = new SignedJWT(header, claims);
    Base64URL sigInput = Base64URL.encode(signedJWT.getSigningInput());
    JWSSigner signer = new RSASSASigner(privateKey);

    signedJWT.sign(signer);

    return signedJWT;
  }

  protected static class TestFilterConfig implements FilterConfig {
    Properties props = null;

    public TestFilterConfig(Properties props) {
      this.props = props;
    }

    @Override
    public String getFilterName() {
      return null;
    }

    /* (non-Javadoc)
     * @see javax.servlet.FilterConfig#getServletContext()
     */
    @Override
    public ServletContext getServletContext() {
//      JWTokenAuthority authority = EasyMock.createNiceMock(JWTokenAuthority.class);
//      GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
//      EasyMock.expect(services.getService("TokenService").andReturn(authority));
//      ServletContext context = EasyMock.createNiceMock(ServletContext.class);
//      EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE).andReturn(new DefaultGatewayServices()));
      return null;
    }

    /* (non-Javadoc)
     * @see javax.servlet.FilterConfig#getInitParameter(java.lang.String)
     */
    @Override
    public String getInitParameter(String name) {
      return props.getProperty(name, null);
    }

    /* (non-Javadoc)
     * @see javax.servlet.FilterConfig#getInitParameterNames()
     */
    @Override
    public Enumeration<String> getInitParameterNames() {
      return null;
    }
    
  }
  
  protected static class TestJWTokenAuthority implements JWTokenAuthority {

    /* (non-Javadoc)
     * @see org.apache.hadoop.gateway.services.security.token.JWTokenAuthority#issueToken(javax.security.auth.Subject, java.lang.String)
     */
    @Override
    public JWTToken issueToken(Subject subject, String algorithm)
        throws TokenServiceException {
      // TODO Auto-generated method stub
      return null;
    }

    /* (non-Javadoc)
     * @see org.apache.hadoop.gateway.services.security.token.JWTokenAuthority#issueToken(java.security.Principal, java.lang.String)
     */
    @Override
    public JWTToken issueToken(Principal p, String algorithm)
        throws TokenServiceException {
      // TODO Auto-generated method stub
      return null;
    }

    /* (non-Javadoc)
     * @see org.apache.hadoop.gateway.services.security.token.JWTokenAuthority#issueToken(java.security.Principal, java.lang.String, java.lang.String)
     */
    @Override
    public JWTToken issueToken(Principal p, String audience, String algorithm)
        throws TokenServiceException {
      return null;
    }

    /* (non-Javadoc)
     * @see org.apache.hadoop.gateway.services.security.token.JWTokenAuthority#verifyToken(org.apache.hadoop.gateway.services.security.token.impl.JWTToken)
     */
    @Override
    public boolean verifyToken(JWTToken token) throws TokenServiceException {
      return true;
    }

    /* (non-Javadoc)
     * @see org.apache.hadoop.gateway.services.security.token.JWTokenAuthority#issueToken(java.security.Principal, java.lang.String, java.lang.String, long)
     */
    @Override
    public JWTToken issueToken(Principal p, String audience, String algorithm,
        long expires) throws TokenServiceException {
      return null;
    }

    @Override
    public JWTToken issueToken(Principal p, List<String> audiences, String algorithm,
        long expires) throws TokenServiceException {
      return null;
    }

    /* (non-Javadoc)
     * @see org.apache.hadoop.gateway.services.security.token.JWTokenAuthority#issueToken(java.security.Principal, java.lang.String, long)
     */
    @Override
    public JWT issueToken(Principal p, String audience, long l)
        throws TokenServiceException {
      // TODO Auto-generated method stub
      return null;
    }
    
  }
  
  protected static class TestFilterChain implements FilterChain {
    boolean doFilterCalled = false;
    Subject subject = null;

    /* (non-Javadoc)
     * @see javax.servlet.FilterChain#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse)
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response)
        throws IOException, ServletException {
      doFilterCalled = true;
      
      subject = Subject.getSubject( AccessController.getContext() );
    }
    
  }
}