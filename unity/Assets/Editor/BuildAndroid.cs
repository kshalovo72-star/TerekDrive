using UnityEditor;

public static class BuildAndroid
{
    public static void Build()
    {
        var scenes = new[]
        {
            "Assets/Scenes/Main.unity"
        };

        var options = new BuildPlayerOptions
        {
            scenes = scenes,
            locationPathName = "Builds/TerekDrive.apk",
            target = BuildTarget.Android,
            options = BuildOptions.None
        };

        BuildPipeline.BuildPlayer(options);
    }
}
