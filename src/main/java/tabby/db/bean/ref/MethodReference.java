package tabby.db.bean.ref;

import lombok.Data;
import org.neo4j.ogm.annotation.Relationship;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import tabby.config.GlobalConfiguration;
import tabby.db.bean.edge.Alias;
import tabby.db.bean.edge.Call;
import tabby.db.converter.Map2JsonStringConverter;
import tabby.db.converter.Set2JsonStringConverter;

import javax.persistence.*;
import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/10/9
 */
@Data
@Entity
@Table(name = "methods")
public class MethodReference {

    @Id
    private String id;

    private String name;
    //    @Column(unique = true)
    @Column(columnDefinition = "TEXT")
    private String signature;

    @Column(length = 1000)
    private String subSignature;
    private String returnType;
    private int modifiers;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = Set2JsonStringConverter.class)
    private Set<String> parameters = new HashSet<>();

    private String classname;
//    @Transient
//    private String body;

    private boolean isSink = false;
    private boolean isSource = false;
    private boolean isStatic = false;
    private boolean isPolluted = false;
    private boolean hasParameters = false;
    private boolean isInitialed = false;
    private boolean actionInitialed = false;
    private boolean isIgnore = false;
    private boolean isSerializable = false;
    /**
     * 污染传递点，主要标记2种类型，this和param
     * 其他函数可以依靠relatedPosition，来判断当前位置是否是通路
     * old=new
     * param-0=other value
     *      param-0=param-1,param-0=this.field
     * return=other value
     *      return=param-0,return=this.field
     * this.field=other value
     * 提示经过当前函数调用后，当前函数参数和返回值的relateType会发生如下变化
     */
    @Column(columnDefinition = "TEXT")
    @Convert(converter = Map2JsonStringConverter.class)
    private Map<String, String> actions = new HashMap<>();

    @Convert(converter = Set2JsonStringConverter.class)
    private Set<Integer> pollutedPosition = new HashSet<>();

    @org.springframework.data.annotation.Transient
    @Relationship(type="CALL")
    private transient Set<Call> callEdge = new HashSet<>();

    /**
     * 父类函数、接口函数的依赖边
     */
    @org.springframework.data.annotation.Transient
    @Relationship(type="ALIAS", direction = "UNDIRECTED")
    private transient Alias aliasEdge;

    // TODO 后续添加分析后的数据字段
    public static MethodReference newInstance(String name, String signature){
        MethodReference methodRef = new MethodReference();
        methodRef.setName(name);
        methodRef.setId(UUID.randomUUID().toString());
        methodRef.setSignature(signature);
        return methodRef;
    }

    public static MethodReference newInstance(String classname, SootMethod method){
        MethodReference methodRef = newInstance(method.getName(), method.getSignature());
        methodRef.setClassname(classname);
        methodRef.setModifiers(method.getModifiers());
        methodRef.setSubSignature(method.getSubSignature());
        methodRef.setStatic(method.isStatic());
        methodRef.setReturnType(method.getReturnType().toString());
        if(method.getParameterCount() > 0){
            methodRef.setHasParameters(true);
            for(int i=0; i<method.getParameterCount();i++){
                List<Object> param = new ArrayList<>();
                param.add(i); // param position
                param.add(method.getParameterType(i).toString()); // param type
                methodRef.getParameters().add(GlobalConfiguration.GSON.toJson(param));
            }
        }
//        methodRef.setCachedMethod(method);
        return methodRef;
    }

    public List<String> toCSV(){
        List<String> csv = new ArrayList<>();
        csv.add(id);
        csv.add(name);
        csv.add(signature);
        csv.add(subSignature);
        csv.add(modifiers+"");
        csv.add(Boolean.toString(isStatic));
        csv.add(Boolean.toString(hasParameters));
        csv.add(Boolean.toString(isSink));
        csv.add(Boolean.toString(isSource));
        csv.add(Boolean.toString(isPolluted));
        csv.add(String.join("|", parameters));
        csv.add(GlobalConfiguration.GSON.toJson(actions));
        csv.add(pollutedPosition != null ? toStr(pollutedPosition) : "");
        csv.add(returnType);
        return csv;
    }

    public String toStr(Map<String, String> positions){
        StringBuilder sb = new StringBuilder();
        positions.forEach((position, related)->{
            sb.append(position).append("~").append(related).append(";");
        });
        return sb.toString();
    }

    public String toStr(Set<Integer> positions){
        StringBuilder sb = new StringBuilder();
        positions.forEach((position)->{
            sb.append(position).append("|");
        });
        return sb.toString();
    }

    public Call findCall(MethodReference target){
        for(Call call:callEdge){
            if(call.getTarget().equals(target)){
                return call;
            }
        }
        return null;
    }

    public SootMethod getMethod(){
        SootMethod method = null;
        try{
            SootClass sc = Scene.v().getSootClass(classname);
            if(!sc.isPhantom()){
                method = sc.getMethod(subSignature);
                return method;
            }
        }catch (Exception ignored){

        }
        return null;
    }

    public void addAction(String key, String value){
        actions.put(key, value);
    }



}