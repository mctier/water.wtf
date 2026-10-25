import com.google.gson.*;
import com.utilitymod.web.*;
import java.net.*;
import java.net.http.*;
import java.util.concurrent.atomic.*;

/** Runs the actual transport and validation with inert module fixtures, never Minecraft actions. */
public final class WebUiTest {
 static int checks;
 static void fixture(WebState.Entry entry,String id,JsonObject schema,JsonElement initial,boolean visible){
  schema.addProperty("group",id.equals("nested")?"Input simulation":"Module settings");
  AtomicReference<JsonElement> value=new AtomicReference<>(initial);
  entry.fields.put(id,new WebState.Field(schema,value::get,value::set,()->visible));
 }
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static void reject(Runnable r,String message){try{r.run();throw new AssertionError(message);}catch(IllegalArgumentException expected){checks++;}}
 static JsonElement json(String s){return JsonParser.parseString(s);}
 static JsonObject request(WebState.Entry e,String setting,JsonElement value){JsonObject r=new JsonObject();r.addProperty("moduleId",e.id);r.addProperty("revision",e.snapshot().get("revision").getAsLong());if(setting!=null)r.addProperty("settingId",setting);r.add("value",value);return r;}
 static HttpResponse<String> http(LocalWebServer server,String path,String method,String body,String auth,String origin)throws Exception{
  var b=HttpRequest.newBuilder(URI.create(server.origin()+path));if(auth!=null)b.header("Authorization",auth);if(origin!=null)b.header("Origin",origin);
  if(method.equals("POST"))b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));else b.GET();
  return HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());
 }
 public static void main(String[] args)throws Exception{
  AtomicBoolean on=new AtomicBoolean(false);AtomicInteger saved=new AtomicInteger();AtomicReference<JsonElement> amount=new AtomicReference<>(json("3"));
  WebState state=new WebState(saved::incrementAndGet);
  WebState.Entry entry=new WebState.Entry("fixture.AutoMace","Auto Mace","Combat","TEST FIXTURE Â· no Minecraft connection",false,on::get,on::set);
  JsonObject number=WebState.bounds(WebState.schema("Min Fall","number"),0,12,1);number.addProperty("integer",true);
  entry.fields.put("fall",new WebState.Field(number,amount::get,amount::set,()->true));
  AtomicBoolean aim=new AtomicBoolean();entry.fields.put("silent",new WebState.Field(WebState.schema("Silent Aim","boolean"),()->new JsonPrimitive(aim.get()),v->aim.set(v.getAsBoolean()),()->true));
  state.add(entry);
  for(String name:new String[]{"Fast Bridge","Sprint","FullBright","Target HUD","Auto Tool","Inventory Preview"}){
   AtomicBoolean active=new AtomicBoolean();state.add(new WebState.Entry("fixture."+name,name,name.equals("Fast Bridge")||name.equals("Sprint")?"Movement":name.contains("HUD")||name.equals("FullBright")?"Visual":"Misc","TEST FIXTURE Â· local dashboard preview",false,active::get,active::set));
  }
  JsonObject select=WebState.schema("Input mode","select");select.add("options",json("[{\"value\":\"direct\",\"label\":\"Direct\"},{\"value\":\"simulated\",\"label\":\"Simulated\"}]"));
  fixture(entry,"mode",select,json("\"direct\""),true);
  fixture(entry,"color",WebState.schema("Target color","color"),json("\"ffc4a1ff\""),true);
  fixture(entry,"bind",WebState.bounds(WebState.schema("Module toggle key","key"),Integer.MIN_VALUE,348,1),json("-1"),true);
  fixture(entry,"range",WebState.bounds(WebState.schema("Attack delay range","range"),0,20,1),json("[2,5]"),true);
  JsonObject multi=select.deepCopy();multi.addProperty("name","Target types");multi.addProperty("type","multi");fixture(entry,"targets",multi,json("[\"direct\"]"),true);
  fixture(entry,"slots",WebState.schema("Allowed hotbar slots","slots"),json("[true,false,false,false,false,false,false,false,false]"),true);
  fixture(entry,"registry",WebState.schema("Allowed items","registry"),json("[\"minecraft:mace\"]"),true);
  fixture(entry,"list",WebState.schema("Custom names","list"),json("[\"Sample\"]"),true);
  fixture(entry,"text",WebState.schema("Label","text"),json("\"Example label\""),true);
  fixture(entry,"conditional",WebState.bounds(WebState.schema("Conditional threshold","number"),0,10,.1),json("3"),false);
  fixture(entry,"nested",WebState.bounds(WebState.schema("Input delay","number"),50,10000,1),json("100"),true);
  if(args.length>0&&args[0].equals("--preview")){
   LocalWebServer server=new LocalWebServer(r->{synchronized(state){return r==null?state.snapshot():state.change(r);}});server.start();
   System.out.println("FIXTURE_PREVIEW="+server.launchUrl());System.out.flush();Thread.sleep(1800000);server.close();return;
  }
  check(state.snapshot().getAsJsonArray("modules").size()==7,"all modules listed");
  long revision=entry.snapshot().get("revision").getAsLong();check(entry.snapshot().get("revision").getAsLong()==revision,"stable revision");
  state.change(request(entry,null,json("true")));check(on.get(),"browser toggle applied");check(saved.get()==1,"persist callback");
  state.change(request(entry,"fall",json("7")));check(amount.get().getAsInt()==7,"setting applied");
  JsonObject stale=request(entry,"fall",json("8"));amount.set(json("9"));
  try{state.change(stale);throw new AssertionError("conflict accepted");}catch(WebState.Conflict expected){checks++;}
  check(entry.snapshot().getAsJsonArray("settings").get(0).getAsJsonObject().get("value").getAsInt()==9,"game change visible");
  reject(()->state.change(request(entry,"fall",json("13"))),"range");
  reject(()->state.change(request(entry,"fall",json("1.5"))),"integer");
  reject(()->state.change(request(entry,"fall",json("\"5\""))),"numeric string");
  reject(()->state.change(request(entry,"missing",json("1"))),"unknown setting");
  reject(()->state.change(request(entry,null,json("1"))),"non-boolean toggle");
  reject(()->state.change(new JsonObject()),"missing module");
  JsonObject extra=request(entry,"fall",json("3"));extra.addProperty("code","run");reject(()->state.change(extra),"unknown field");
  JsonObject range=WebState.bounds(WebState.schema("Range","range"),0,10,0.1);
  WebState.validate(range,json("[1,8]"));checks++;reject(()->WebState.validate(range,json("[8,1]")),"inverted range");reject(()->WebState.validate(range,json("[1]")),"short range");
  JsonObject mode=WebState.schema("Mode","select");mode.add("options",json("[{\"value\":\"a\",\"label\":\"A\"}]"));
  WebState.validate(mode,json("\"a\""));checks++;reject(()->WebState.validate(mode,json("\"b\"")),"enum");
  JsonObject color=WebState.schema("Color","color");WebState.validate(color,json("\"ff12ab89\""));checks++;reject(()->WebState.validate(color,json("\"x\"")),"color");
  JsonObject text=WebState.schema("Text","text");text.addProperty("maxLength",3);reject(()->WebState.validate(text,json("\"abcd\"")),"text length");
  JsonObject key=WebState.schema("Key","key");WebState.validate(key,json("-2147483648"));checks++;reject(()->WebState.validate(key,json("-50")),"invalid key");
  JsonObject slots=WebState.schema("Slots","slots");WebState.validate(slots,json("[true,false,false,false,false,false,false,false,false]"));checks++;reject(()->WebState.validate(slots,json("[true]")),"slots");
  AtomicBoolean action=new AtomicBoolean();WebState.Entry dangerous=new WebState.Entry("fixture.action","Action","Misc","Confirmation fixture",true,action::get,action::set);state.add(dangerous);
  reject(()->state.change(request(dangerous,null,json("true"))),"confirmation");JsonObject confirmed=request(dangerous,null,json("true"));confirmed.addProperty("confirmed",true);state.change(confirmed);check(action.get(),"confirmed action");
  entry.fields.put("hidden",new WebState.Field(number,amount::get,amount::set,()->false));state.change(request(entry,"hidden",json("1")));check(amount.get().getAsInt()==1,"conditional fields remain editable");
  AtomicReference<JsonElement> secret=new AtomicReference<>();entry.fields.put("secret",new WebState.Field(text,()->JsonNull.INSTANCE,secret::set,()->true));
  long before=entry.snapshot().get("revision").getAsLong();state.change(request(entry,"secret",json("\"abc\"")));check(entry.snapshot().get("revision").getAsLong()>before,"private mutation revision");
  JsonArray controls=entry.snapshot().getAsJsonArray("settings");
  for(JsonElement item:controls){JsonObject control=item.getAsJsonObject();if(control.get("value").isJsonNull())continue;
   state.change(request(entry,control.get("id").getAsString(),control.get("value")));
   check(true,"roundtrip "+control.get("type").getAsString());
  }
  WebState.Entry settingsOnly=new WebState.Entry("fixture.settings","Appearance","Client","Settings only",false,()->true,v->{});settingsOnly.toggleable=false;state.add(settingsOnly);
  reject(()->state.change(request(settingsOnly,null,json("false"))),"settings-only toggle rejected");
  try(LocalWebServer server=new LocalWebServer(r->{synchronized(state){return r==null?state.snapshot():state.change(r);}})){
   server.start();String token=server.launchUrl().split("token=")[1],auth="Bearer "+token;
   check(http(server,"/","GET",null,null,null).statusCode()==200,"bundled HTML");
   check(http(server,"/app.js","GET",null,null,null).statusCode()==200,"bundled JS");
   check(http(server,"/style.css","GET",null,null,null).statusCode()==200,"bundled CSS");
   check(http(server,"/api/state","GET",null,null,null).statusCode()==401,"missing token");
   check(http(server,"/api/state","GET",null,"Bearer wrong",null).statusCode()==401,"wrong token");
   var snapshot=http(server,"/api/state","GET",null,auth,null);check(snapshot.statusCode()==200,"authenticated snapshot");check(snapshot.headers().firstValue("Cache-Control").orElse("").equals("no-store"),"no cache");
   check(snapshot.headers().firstValue("Content-Security-Policy").orElse("").contains("frame-ancestors 'none'"),"CSP");
   String body=request(entry,"fall",json("6")).toString();
   check(http(server,"/api/change","POST",body,auth,"https://example.com").statusCode()==403,"foreign origin");
   check(http(server,"/api/change","POST",body,auth,null).statusCode()==403,"missing origin");
   check(http(server,"/api/change","POST",body,auth,server.origin()).statusCode()==200,"HTTP write");check(amount.get().getAsInt()==6,"HTTP applied state");
   check(http(server,"/api/change","POST",body,auth,server.origin()).statusCode()==409,"HTTP stale revision");
   check(http(server,"/api/change","POST","{",auth,server.origin()).statusCode()==400,"invalid JSON");
   check(http(server,"/api/change","POST","x".repeat(16385),auth,server.origin()).statusCode()==413,"payload bound");
   check(http(server,"/../build.gradle","GET",null,auth,null).statusCode()==404,"no arbitrary files");
   check(http(server,"/api/state","GET",null,auth,"https://example.com").statusCode()==403,"cross origin read");
  }
  System.out.println("PASS: "+checks+" web UI assertions (real HTTP transport, inert module fixtures)");
 }
}
