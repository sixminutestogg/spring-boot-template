package site.yuyanjia.template.website.realm;

import org.apache.commons.collections.CollectionUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ByteSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import site.yuyanjia.template.common.mapper.WebPermissionMapper;
import site.yuyanjia.template.common.mapper.WebRolePermissionMapper;
import site.yuyanjia.template.common.mapper.WebUserMapper;
import site.yuyanjia.template.common.mapper.WebUserRoleMapper;
import site.yuyanjia.template.common.model.WebPermissionDO;
import site.yuyanjia.template.common.model.WebRolePermissionDO;
import site.yuyanjia.template.common.model.WebUserDO;
import site.yuyanjia.template.common.model.WebUserRoleDO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;


/**
 * 用户Realm
 *
 * @author seer
 * @date 2018/2/1 16:59
 */
public class WebUserRealm extends AuthorizingRealm {

    @Autowired
    private WebUserMapper webUserMapper;

    @Autowired
    private WebUserRoleMapper webUserRoleMapper;

    @Autowired
    private WebRolePermissionMapper webRolePermissionMapper;

    @Autowired
    private WebPermissionMapper webPermissionMapper;

    /**
     * 获取授权信息
     *
     * @param principalCollection
     * @return
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principalCollection) {
        WebUserDO webUserDO = (WebUserDO) principalCollection.getPrimaryPrincipal();
        if (ObjectUtils.isEmpty(webUserDO) || ObjectUtils.isEmpty(webUserDO.getId())) {
            throw new AccountException("用户信息查询为空");
        }

        SimpleAuthorizationInfo authenticationInfo = new SimpleAuthorizationInfo();
        List<WebUserRoleDO> webUserRoleDOList = webUserRoleMapper.selectByUserId(webUserDO.getId());
        if (CollectionUtils.isEmpty(webUserRoleDOList)) {
            return authenticationInfo;
        }

        List<String> roleList = webUserRoleDOList.stream()
                .map(webUserRoleDO -> String.valueOf(webUserRoleDO.getRoleId()))
                .collect(Collectors.toList());
        authenticationInfo.addRoles(roleList);

        List<WebRolePermissionDO> webRolePermissionDOList = new ArrayList<>();
        webUserRoleDOList.forEach(
                webUserRoleDO -> webRolePermissionDOList.addAll(webRolePermissionMapper.selectByRoleId(webUserRoleDO.getRoleId()))
        );
        if (CollectionUtils.isEmpty(webRolePermissionDOList)) {
            return authenticationInfo;
        }

        List<String> permissonList = webRolePermissionDOList.stream()
                .filter(distinctByKey(WebRolePermissionDO::getPermissionId))
                .map(
                        webRolePermissionDO ->
                        {
                            WebPermissionDO webPermissionDO = webPermissionMapper.selectByPrimaryKey(webRolePermissionDO.getPermissionId());
                            return webPermissionDO.getPermissionValue();
                        }
                ).collect(Collectors.toList());
        authenticationInfo.addStringPermissions(permissonList);
        return authenticationInfo;
    }

    /**
     * 获取验证信息
     *
     * @param authenticationToken
     * @return
     * @throws AuthenticationException
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken) throws AuthenticationException {
        String username = (String) authenticationToken.getPrincipal();
        WebUserDO webUserDO = webUserMapper.selectByUsername(username);
        if (ObjectUtils.isEmpty(webUserDO)) {
            throw new UnknownAccountException("用户 " + username + " 信息查询失败");
        }

        SimpleAuthenticationInfo authenticationInfo = new SimpleAuthenticationInfo(
                webUserDO,
                webUserDO.getPassword(),
                getName()
        );
        ByteSource salt = ByteSource.Util.bytes(webUserDO.getSalt());
        authenticationInfo.setCredentialsSalt(salt);
        return authenticationInfo;
    }

    /**
     * 删除缓存
     *
     * @param principals
     */
    @Override
    protected void doClearCache(PrincipalCollection principals) {
        super.doClearCache(principals);
    }

    /**
     * 去重过滤器
     *
     * @param keyExtractor
     * @param <T>
     * @return
     */
    private static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>(16);
        return object -> seen.putIfAbsent(keyExtractor.apply(object), Boolean.TRUE) == null;
    }
}
