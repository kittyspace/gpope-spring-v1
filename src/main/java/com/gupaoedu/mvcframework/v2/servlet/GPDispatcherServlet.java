package com.gupaoedu.mvcframework.v2.servlet;

import com.gupaoedu.mvcframework.annotation.*;
import org.omg.CORBA.CODESET_INCOMPATIBLE;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * @Discription: 2.0版本你看到了哪些设计模式？
 * 工厂、单例、委派、模板模式(init里面就是)、策略模式（handleMapping)（if else）
 * @Author: Created by lyan on 2019/11/22 16:07
 */
public class GPDispatcherServlet extends HttpServlet {

    //存储aplication.properties的配置内容
    private Properties contextConfig = new Properties();
    //存储所有扫描到的类
    private List<String> classNames = new ArrayList<String>();
    //传说中的Ioc容器，我们来揭开他的神秘面纱
    //为了简化程序，暂时不考虑ConcurrentHashMap,主要还是关注设计思想和原理
    private Map<String,Object> ioc = new HashMap<String,Object>();
    //保存url和method的对应关系
    private Map<String,Method> handleMapping = new HashMap<String,Method>();


    @Override
    public void init(ServletConfig config) throws ServletException {
        //1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2、扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));
        //3、初始化所有相关的类的实例，并且放入到IOC容器之中
        doInstance();
        //4、完成依赖注入
        doAutowired();
        //5、初始化HandlerMapping
        initHandlerMapping();

        System.out.println("GP Spring framework is init.");
        
    }

    //初始化url和Method的一对一的对应关系
    private void initHandlerMapping() {

        if(ioc.isEmpty()){
            return ;
        }


        for(Map.Entry<String,Object> entry : ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
             if(!clazz.isAnnotationPresent(GPController.class)){
                 continue;
             }

            //保存写在类上面的@GPRequestMapping("/demo")
            String baseUrl = "";
             if(clazz.isAnnotationPresent(GPRequestMapping.class)){
                 GPRequestMapping requestMapping = clazz.getAnnotation(GPRequestMapping.class);
                 baseUrl = requestMapping.value();

             }

             //默认获取所有的public方法
            for(Method method : clazz.getMethods()){
                if(!method.isAnnotationPresent(GPRequestMapping.class)){
                    continue;
                }

                GPRequestMapping requestMapping = method.getAnnotation(GPRequestMapping.class);
                //优化
                // //demo//query///
                String url = "/"+baseUrl + "/" + requestMapping.value().replaceAll("/+","/");
                //
                handleMapping.put(url,method);
                System.out.println("Mapped:"+url+","+method);

            }
        }
    }


    //自动依赖注入
    private void doAutowired() {
        if(ioc.isEmpty()){
            return;
        }

        for(Map.Entry<String,Object> entry : ioc.entrySet()){
            //Declared 所有的，特定的 字段，包括private/protected/default
            //正常来说，普通的OOP编程只能拿到public的属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for(Field field: fields){
                if(!field.isAnnotationPresent(GPAutowired.class)){
                   continue;
                }
                GPAutowired autowired = field.getAnnotation(GPAutowired.class);

                //如果用户没有自定义beanName,默认就是根据类型注入
                //这个对方省去了对类名首字母小写的情况的判断，这个作为课后作业，
                // 小伙伴们自己去完善
                String beanName = autowired.value().trim();
                if("".equals(beanName)){
                    //如果用户没有自定义beanName的话默认用类型
                    //获得接口的类型，作为key待会拿这个key到ioc容器中去取值
                    beanName = field.getType().getName();
                }

                //如果是public以外的修饰符，只要加了@Autowired注解，都要强制复制
                //反射中叫做暴力访问，强吻
                field.setAccessible(true);

                try {
                    //用反射机制动态给字段赋值
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }

            }
        }
    }


    private void doInstance()  {

        //初始化 ，会为DI做准备
        if(classNames.isEmpty()){
            return;
        }
        try {
            for (String className:classNames){
                Class<?> clazz = Class.forName(className);
                //什么样的类才需要初始化呢？
                //加了注解的类，才需要初始化，怎么判断？
                if(clazz.isAnnotationPresent(GPController.class) ){
                    Object instance = clazz.newInstance();
                    //spring 默认是类名首字母小写
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,instance);

                }else if(clazz.isAnnotationPresent(GPService.class)){

                    //1、自定义的beanName
                    GPService service = clazz.getAnnotation(GPService.class);
                    String beanName = service.value();

                    //2、默认类名首字母小写
                    if("".equals(beanName)){
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);
                    //3、根据类型自动赋值,投机取巧的方式(把类里的所有接口都扫描出来)
                    for(Class<?> i : clazz.getInterfaces()){
                        if(ioc.containsKey(i.getName())){//此处存在一个异常，因为一个接口有可能被多个类实现，此时，就意味着多个实现类具有同一个key都了

                            throw new Exception("The "+i.getName()+" is exists!!");
                        }
                        //把接口的类型直接当成key了
                        ioc.put(i.getName(),instance);

                    }
                }else{
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }


    }

    //如果类名本身是小写字母，确实会出问题
    //但是我要说明的是，这个方法是我自己用，private的
    //传值也是自己传，类也都遵循了驼峰命名法
    //默认传入的值不存在首字母小写的情况，也不可能出现非字母的情况

    //为了简化程序逻辑，就不做其他判断了，大家了解了就ok,
    // 其实用写注释的时间都能够把逻辑写完了
    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        //之所以 加，是因为大小写字母的ASCII码相差32，而且大写字母的ASCII码要小于小写字母的ASCII码
        //在java中，对char做算学运算，实际上就是对ASCII码做算学运算
        chars[0] += 32;
        return String.valueOf(chars);

    }


    private void doScanner(String scanPackage) {
        //包传过来包下面的所有的类全部扫描进来的
        URL url = this.getClass().getClassLoader()
                .getResource("/" + scanPackage.replaceAll("\\.","/"));
        File classPath = new File(url.getFile());

        for (File file : classPath.listFiles()) {
            if(file.isDirectory()){
                doScanner(scanPackage + "." + file.getName());
            }else {
                if(!file.getName().endsWith(".class")){ continue; }
                String className = (scanPackage + "." + file.getName()).replace(".class","");
                classNames.add(className);
            }
        }
    }

    private void doLoadConfig(String parameter) {

        InputStream fis = this.getClass().getClassLoader().getResourceAsStream(parameter);

        try {
            contextConfig.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //调用
        try {
            doDispatch(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception.Detail:"+Arrays.toString(e.getStackTrace()));
        }
    }

    //这个思路清楚，但是这个版本代码还是混乱。直接看第三个版本
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception{
        //绝对路径
        String url = req.getRequestURI();

        String contextPath = req.getContextPath();

        url = url.replaceAll(contextPath,"").replaceAll("/+","/");

        if(!this.handleMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found!!!");
            return;
        }

        Method method = this.handleMapping.get(url);

        //投机取巧的方式
        //通过反射拿到method所在的class,拿到class之后还是拿到class的名称，
        //再调用toLowerFirstCase 获得beanName
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());

        //从request中拿到url传过来的参数
        Map<String,String[]> params = req.getParameterMap();

        Class<?>[] parameterTypes = method.getParameterTypes();

        Object[] paramValues = new Object[parameterTypes.length];
        for(int i = 0; i< parameterTypes.length; i++ ){
            Class parameterType = parameterTypes[i];
            // 不能用instanceof ,parameterType它不是实参，是形参
            if(parameterType == HttpServletRequest.class){
                paramValues[i] = req;
                continue;
            }else if(parameterType == HttpServletResponse.class){
                paramValues[i] = resp;
                continue;
            }

                //把方法上的注解拿到，得到的是一个二维数组
                Annotation [] [] pa = method.getParameterAnnotations();
                for (int j = 0; j < pa.length ; j ++) {
                    for(Annotation a : pa[j]){
                        //我们要解析的只是GPRequestParam
                        if(a instanceof GPRequestParam){
                            //拿到参数名称，去http://localhost/demo/query?name=Tom匹配
                            String paramName = ((GPRequestParam) a).value();
                            //从req拿到的参数列表中去找对应的key
                            if(params.containsKey(paramName)){

                                    //拿到key对应的值，而拿到的这个值，有一对多的关系（一个key为name,可能对应多个值Tome,Mic等）
                                    //一个key对应一个数组
                                    System.out.println(Arrays.toString(params.get(paramName)));
                                    //而对方接收的是string类型，所以把数组统一处理为string
                                    //把[或者]这两个符号去掉，再把空格换为逗号
                                    String value = Arrays.toString(params.get(paramName)).replaceAll("\\[|\\]", "")
                                            .replaceAll("\\s", ",");
                                    //类型的强制转换
                                    //整体思路是对的，但是运行时结果会有问题，而且代码很乱所以暂且不看这个。重新找个新的解决办法，去升级到3.0版本
                                    //烂尾项目，你们可以尝试扫尾
                                    paramValues[i] = convent(parameterType,value);
                            }
                        }
                    }
                }

                //http://localhost/demo/query?name=Tom
                //params key="name" value=["Tom"]
                GPRequestParam requestParam = (GPRequestParam)parameterType.getAnnotation(GPRequestParam.class);



        }

        method.invoke(ioc.get(beanName), req,resp,params.get("name")[0]);


    }
    //url传过来的参数都是string类型的，HTTP是基于字符串协议
    //只需要把String转换为任意类型就好了
    private Object convent(Class<?> type,String value){
        //若是int
        if(Integer.class == type){
            return Integer.valueOf(value);
        }else if(String.class == type){
            return value;
        }
        //如果还有double或者其他类型，继续加if
        //这时候，我们应该想到策略模式了
        //在这里暂时不实现，希望自己来实现
        return value;
    }

}
