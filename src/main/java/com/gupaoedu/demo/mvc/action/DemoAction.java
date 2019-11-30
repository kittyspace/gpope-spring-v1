package com.gupaoedu.demo.mvc.action;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.gupaoedu.demo.service.IDemoService;
import com.gupaoedu.mvcframework.annotation.GPAutowired;
import com.gupaoedu.mvcframework.annotation.GPController;
import com.gupaoedu.mvcframework.annotation.GPRequestMapping;
import com.gupaoedu.mvcframework.annotation.GPRequestParam;

@GPController
@GPRequestMapping("/demo")
public class DemoAction {

  	@GPAutowired private IDemoService demoService;

//	@GPRequestMapping("/query")
	//这里如果是/query的话浏览器里必须为/query?name=Tom,
    // 还可以把url匹配正则 如query.* 这样的话，在浏览器里访问为/query?name=Tom或/queryXXX?name=Tome等 都可以
	@GPRequestMapping("/query.*")
	public void query(HttpServletRequest req, HttpServletResponse resp,
					  @GPRequestParam("name") String name){
//		String result = demoService.get(name);
		String result = "My name is " + name;
		try {
			resp.getWriter().write(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@GPRequestMapping("/add")
	public void add(HttpServletRequest req, HttpServletResponse resp,
					@GPRequestParam("a") Integer a, @GPRequestParam("b") Integer b){
		try {
			resp.getWriter().write(a + "+" + b + "=" + (a + b));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@GPRequestMapping("/remove")
	public String remove(@GPRequestParam("id") Integer id){
		return ""+id;
	}

}
